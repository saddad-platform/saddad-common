package com.sadad.common.otp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Every write to the attempt and send counters, each in its own transaction.
 *
 * <p><b>Why the writes are separated from the decisions.</b> Recording a failed attempt and
 * then throwing to reject it are one operation to the caller and two to the database: the
 * throw rolls the transaction back, taking the record of the failure with it. So every wrong
 * code was counted and immediately forgotten, and the lockout could never fire however many
 * times somebody guessed. In-memory test fakes have no transaction to roll back, so the unit
 * tests were silent about it; it was found by driving four wrong codes at the running
 * platform.
 *
 * <p><b>Why each method loads its own row.</b> Reading the lockout in the service and handing
 * the entity here makes it detached: the version it carries is a snapshot, and by the time
 * this commits, the row may have moved on - or been deleted by a successful sign-in - and
 * Hibernate fails the update with a stale-state error. Every method below therefore reads and
 * writes inside the same transaction, and the service passes only a purpose and a subject.
 *
 * <p>A separate bean rather than {@code REQUIRES_NEW} methods on {@link OtpService}, because
 * Spring's transaction advice is a proxy: a service calling its own annotated method calls it
 * directly, and the annotation does nothing at all.
 *
 * <p><b>Why the counter writes retry.</b> Two attempts on the same subject at the same instant
 * both read the row at one version and both try to write the next, and one of them loses. That
 * surfaced as an unhandled 500 on sign-in - and, worse, the loser's increment was simply
 * discarded. Parallel attempts are not an unusual load pattern for these counters; they are
 * what an attacker generates, so the control was failing under precisely the conditions it
 * exists to handle. Each counter write now runs in its own transaction and is retried on a
 * lost race, re-reading the row so nothing is undercounted. The same retry covers two requests
 * both finding no row and both inserting one, which the unique constraint on
 * (purpose, subject) turns into an integrity violation for whichever arrives second.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OtpStateWriter {

    /**
     * Attempts per write. Three is enough for any realistic contention on one subject's row -
     * each retry re-reads and the loser is not repeatedly unlucky - while still being a bound
     * rather than a loop that can spin.
     */
    private static final int MAX_ATTEMPTS = 3;

    private final OtpChallengeRepository challengeRepository;
    private final OtpLockoutRepository lockoutRepository;
    private final PlatformTransactionManager transactionManager;

    /**
     * The current counters for this subject, with a stale window already reset.
     *
     * <p>Read-only, and used by the service to decide whether to refuse before it writes
     * anything. The same reset rule is applied again inside each write, so a decision made
     * on this snapshot cannot be undone by a window that turned over in between.
     *
     * <p><b>Why this returns a copy, read in a transaction of its own.</b> The callers are
     * themselves transactional (every sign-in, reset and change flow is one
     * {@code @Transactional} service method), so a {@code readOnly} annotation here would
     * merely join their transaction, and the row would come back <i>managed</i> by their
     * persistence context. Applying the window reset to a managed entity marks it dirty; the
     * counter write that follows ({@link #registerSend}) then bumps the row's version in its
     * own transaction; and when the caller's transaction finally commits, Hibernate flushes
     * the stale dirty snapshot and fails with an optimistic-lock error - a 500 on sign-in,
     * seen in the running platform exactly when a subject's window had expired. Reading in a
     * separate transaction and handing back a detached value means the snapshot can never be
     * flushed by anyone.
     */
    public OtpLockout snapshot(String purpose, String subject, OtpPolicy policy, Instant now) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.setReadOnly(true);
        OtpLockout row = template.execute(status ->
                lockoutRepository.findByPurposeAndSubject(purpose, subject)
                        .map(OtpStateWriter::detachedCopy)
                        .orElseGet(() -> newLockout(purpose, subject, now)));
        return resetIfWindowOver(row, policy, now);
    }

    /** Records an issued challenge and charges it against the send limits. */
    public void registerSend(String purpose, String subject, OtpChallenge challenge,
                             OtpPolicy policy, Instant now) {
        inOwnTransactionWithRetry("registerSend", subject, () -> {
            challengeRepository.save(challenge);
            OtpLockout lockout = loadForUpdate(purpose, subject, policy, now);
            lockout.setSendsInWindow(lockout.getSendsInWindow() + 1);
            lockout.setLastSentAt(now);
            lockoutRepository.save(lockout);
            return null;
        });
    }

    /**
     * Counts one wrong code, locking if it takes the subject past the limit.
     *
     * @return the lock expiry if this failure caused a lock, otherwise null
     */
    public Instant registerFailure(String purpose, String subject, List<UUID> attemptedIds,
                                   OtpPolicy policy, Instant now) {
        return inOwnTransactionWithRetry("registerFailure", subject, () -> {
            for (UUID id : attemptedIds) {
                challengeRepository.findById(id).ifPresent(challenge -> {
                    challenge.setAttempts(challenge.getAttempts() + 1);
                    challengeRepository.save(challenge);
                });
            }

            OtpLockout lockout = loadForUpdate(purpose, subject, policy, now);
            lockout.setFailedCount(lockout.getFailedCount() + 1);
            // "3 attempts" means three are tolerated and the next is not, so the fourth wrong
            // code locks - which is the behaviour that was asked for.
            if (lockout.getFailedCount() > policy.trialsLimit()) {
                lockout.setLockedUntil(now.plus(Duration.ofMinutes(policy.trialsResetMinutes())));
            }
            lockoutRepository.save(lockout);
            return lockout.getLockedUntil();
        });
    }

    /** Locks with no failed code behind it - the send ceiling reaching its limit. */
    public void lock(String purpose, String subject, OtpPolicy policy, Instant now) {
        inOwnTransactionWithRetry("lock", subject, () -> {
            OtpLockout lockout = loadForUpdate(purpose, subject, policy, now);
            lockout.setLockedUntil(now.plus(Duration.ofMinutes(policy.trialsResetMinutes())));
            lockoutRepository.save(lockout);
            return null;
        });
    }

    /** A correct code: consume it so it cannot be replayed, and clear the tally. */
    public void consume(UUID challengeId, String purpose, String subject, Instant now) {
        inOwnTransactionWithRetry("consume", subject, () -> {
            challengeRepository.findById(challengeId).ifPresent(challenge -> {
                challenge.setConsumedAt(now);
                challengeRepository.save(challenge);
            });
            // Somebody who has just proved they hold the phone is not the flood these counters
            // guard against, so the send ceiling is cleared along with the failures.
            lockoutRepository.findByPurposeAndSubject(purpose, subject)
                    .ifPresent(lockoutRepository::delete);
            return null;
        });
    }

    // ---------------------------------------------------------------------------------

    /**
     * Runs one counter write in a transaction of its own, retrying if it loses a race.
     *
     * <p>A {@link TransactionTemplate} rather than {@code @Transactional}, for the same reason
     * this class exists at all: the annotation is applied by a proxy, so a retry loop calling
     * an annotated method on {@code this} would re-run the body outside any transaction. The
     * template starts a genuinely new one per attempt, which is also what makes the retry
     * correct - the work is re-read and re-applied against the row as it now stands, rather
     * than replayed from a stale snapshot.
     *
     * <p>Only conflicts are retried. Any other failure is the caller's to see.
     */
    private <T> T inOwnTransactionWithRetry(String operation, String subject, Supplier<T> work) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        RuntimeException lastConflict = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return template.execute(status -> work.get());
            } catch (OptimisticLockingFailureException | DataIntegrityViolationException conflict) {
                lastConflict = conflict;
                // Subject only - never the code, and never the exception's own message, which
                // can carry row contents into the log.
                log.debug("OTP {} for subject hash {} lost a write race (attempt {} of {})",
                        operation, Integer.toHexString(subject.hashCode()), attempt, MAX_ATTEMPTS);
            }
        }
        throw lastConflict;
    }

    private OtpLockout loadForUpdate(String purpose, String subject, OtpPolicy policy, Instant now) {
        return resetIfWindowOver(
                lockoutRepository.findByPurposeAndSubject(purpose, subject)
                        .orElseGet(() -> newLockout(purpose, subject, now)),
                policy, now);
    }

    /**
     * A value copy of a row: same counters, no identity, so it is never part of any
     * persistence context and can be mutated freely by the caller's window reset.
     */
    private static OtpLockout detachedCopy(OtpLockout row) {
        return OtpLockout.builder()
                .purpose(row.getPurpose())
                .subject(row.getSubject())
                .failedCount(row.getFailedCount())
                .lockedUntil(row.getLockedUntil())
                .windowStartedAt(row.getWindowStartedAt())
                .sendsInWindow(row.getSendsInWindow())
                .lastSentAt(row.getLastSentAt())
                .build();
    }

    private OtpLockout newLockout(String purpose, String subject, Instant now) {
        return OtpLockout.builder().purpose(purpose).subject(subject).windowStartedAt(now).build();
    }

    /** A window that has run its course starts again - otherwise a single failure days ago
     * would still count towards today's limit. A live lock is left alone. */
    private OtpLockout resetIfWindowOver(OtpLockout lockout, OtpPolicy policy, Instant now) {
        Duration window = Duration.ofMinutes(policy.trialsResetMinutes());
        boolean over = lockout.getWindowStartedAt() == null
                || Duration.between(lockout.getWindowStartedAt(), now).compareTo(window) >= 0;
        if (over && !lockout.isLocked(now)) {
            lockout.setWindowStartedAt(now);
            lockout.setFailedCount(0);
            lockout.setSendsInWindow(0);
            lockout.setLockedUntil(null);
        }
        return lockout;
    }
}
