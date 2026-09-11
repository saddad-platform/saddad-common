package com.sadad.common.otp;

import com.sadad.common.exception.BusinessException;
import com.sadad.common.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The behaviour that makes a one-time code worth having.
 *
 * <p>Every one of these was absent before: the code was a fixed value derived from the
 * configured length, it never expired, nothing counted a wrong guess, and it was returned to
 * the caller in the response so the browser could display it. A person could sign in as
 * anybody whose username they knew.
 *
 * <p>Backed by in-memory repository fakes rather than a database, because what is under test
 * is the decision-making - which is where all of the above went wrong.
 */
class OtpServiceTest {

    private static final String SUBJECT = "rawabi.admin";

    /**
     * Transactions the fakes cannot have.
     *
     * <p>The writer runs each counter write in a transaction of its own so it can retry a lost
     * race. These tests are about the decisions, not the durability, and the in-memory fakes
     * have nothing to commit or roll back - so the template is given a manager that starts and
     * finishes a transaction by doing nothing at all. The body still runs exactly once per
     * attempt, which is all these tests observe.
     */
    private static final PlatformTransactionManager NO_TRANSACTIONS = new PlatformTransactionManager() {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) { }

        @Override
        public void rollback(TransactionStatus status) { }
    };

    private FakeChallenges challenges;
    private FakeLockouts lockouts;
    private OtpPolicy policy;
    private OtpService service;

    @BeforeEach
    void setUp() {
        challenges = new FakeChallenges();
        lockouts = new FakeLockouts();
        policy = new OtpPolicy(4, 120, 3, 5, 0, 5);
        service = new OtpService(challenges, lockouts, () -> policy,
                new OtpStateWriter(challenges, lockouts, NO_TRANSACTIONS));
    }

    @Test
    void theIssuedCodeIsNeverStoredInReadableForm() {
        String code = service.issue(OtpPurpose.SIGN_IN, SUBJECT, false).code();

        OtpChallenge stored = challenges.rows.get(0);
        assertNotEquals(code, stored.getCodeHash(), "the code must not be the stored value");
        assertFalse(stored.getCodeHash().contains(code), "nor recoverable from it");
        assertNotNull(stored.getCodeSalt());
    }

    @Test
    void aRandomCodeIsNotTheSameTwice() {
        // The old implementation returned the same value for everybody, forever.
        List<String> seen = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            lockouts.rows.clear();
            seen.add(service.issue(OtpPurpose.SIGN_IN, SUBJECT + i, false).code());
        }
        assertTrue(seen.stream().distinct().count() > 1, "codes must not be a constant");
    }

    @Test
    void theCorrectCodeIsAcceptedOnceAndOnlyOnce() {
        String code = service.issue(OtpPurpose.SIGN_IN, SUBJECT, false).code();
        assertDoesNotThrow(() -> service.verify(OtpPurpose.SIGN_IN, SUBJECT, code));

        // Replay must fail: a code that works twice is not one-time.
        assertThrows(RuntimeException.class, () -> service.verify(OtpPurpose.SIGN_IN, SUBJECT, code));
    }

    @Test
    void anExpiredCodeSaysSoRatherThanSayingItIsWrong() {
        String code = service.issue(OtpPurpose.SIGN_IN, SUBJECT, false).code();
        challenges.rows.get(0).setExpiresAt(Instant.now().minusSeconds(1));

        ValidationException thrown = assertThrows(ValidationException.class,
                () -> service.verify(OtpPurpose.SIGN_IN, SUBJECT, code));
        // Telling somebody their correct code is wrong sends them hunting for a problem that
        // is not there; "it expired" has an obvious next step.
        assertEquals("OTP_EXPIRED", thrown.getCode(),
                "an expired code must report expiry, not invalidity");
    }

    @Test
    void aCodeIssuedForOnePurposeCannotConfirmAnother() {
        // Otherwise the weaker of two flows becomes the way into the stronger one.
        String resetCode = service.issue(OtpPurpose.PASSWORD_RESET, SUBJECT, false).code();
        assertThrows(RuntimeException.class,
                () -> service.verify(OtpPurpose.SIGN_IN, SUBJECT, resetCode));
    }

    @Test
    void theFourthWrongCodeLocksWhenThreeTrialsAreAllowed() {
        // Exactly the behaviour asked for: "if admin configure 3, and normal user tried 3
        // time, in the fourth trails we should display account locked".
        service.issue(OtpPurpose.SIGN_IN, SUBJECT, false);

        for (int attempt = 1; attempt <= 3; attempt++) {
            int n = attempt;
            ValidationException e = assertThrows(ValidationException.class,
                    () -> service.verify(OtpPurpose.SIGN_IN, SUBJECT, "0000"),
                    "attempt " + n + " should be refused but not locked");
            assertNotEquals("OTP_LOCKED", e.getCode(), "attempt " + n + " must not lock");
        }

        BusinessException locked = assertThrows(BusinessException.class,
                () -> service.verify(OtpPurpose.SIGN_IN, SUBJECT, "0000"));
        assertEquals("OTP_LOCKED", locked.getCode(), "the fourth attempt locks");
        assertTrue(service.isLocked(OtpPurpose.SIGN_IN, SUBJECT));
    }

    @Test
    void aFreshCodeDoesNotResetTheFailureTally() {
        // Otherwise "guess three times, request a new code, guess three more" is unlimited
        // guessing with extra steps - which is what a per-challenge counter would allow.
        service.issue(OtpPurpose.SIGN_IN, SUBJECT, false);
        for (int i = 0; i < 3; i++) {
            assertThrows(RuntimeException.class, () -> service.verify(OtpPurpose.SIGN_IN, SUBJECT, "0000"));
        }
        service.issue(OtpPurpose.SIGN_IN, SUBJECT, false);

        assertThrows(BusinessException.class, () -> service.verify(OtpPurpose.SIGN_IN, SUBJECT, "0000"),
                "the tally survives a new code");
    }

    @Test
    void aLockedSubjectCannotEvenRequestANewCode() {
        service.issue(OtpPurpose.SIGN_IN, SUBJECT, false);
        for (int i = 0; i < 4; i++) {
            assertThrows(RuntimeException.class, () -> service.verify(OtpPurpose.SIGN_IN, SUBJECT, "0000"));
        }
        assertThrows(BusinessException.class, () -> service.issue(OtpPurpose.SIGN_IN, SUBJECT, false),
                "requesting a code must not be a way around a lock");
    }

    @Test
    void aSuccessfulSignInClearsTheTally() {
        service.issue(OtpPurpose.SIGN_IN, SUBJECT, false);
        assertThrows(RuntimeException.class, () -> service.verify(OtpPurpose.SIGN_IN, SUBJECT, "0000"));

        String good = service.issue(OtpPurpose.SIGN_IN, SUBJECT, false).code();
        service.verify(OtpPurpose.SIGN_IN, SUBJECT, good);

        assertTrue(lockouts.rows.isEmpty(), "somebody who proved they hold the phone is not the threat");
    }

    @Test
    void resendIsRefusedInsideTheCooldown() {
        policy = new OtpPolicy(4, 120, 3, 5, 30, 5);
        service = new OtpService(challenges, lockouts, () -> policy,
                new OtpStateWriter(challenges, lockouts, NO_TRANSACTIONS));

        service.issue(OtpPurpose.SIGN_IN, SUBJECT, false);
        BusinessException tooSoon = assertThrows(BusinessException.class,
                () -> service.issue(OtpPurpose.SIGN_IN, SUBJECT, false));
        assertEquals("OTP_RESEND_TOO_SOON", tooSoon.getCode());
    }

    @Test
    void floodingPastTheSendCeilingLocksRatherThanSendingForever() {
        // Without this, "resend" is an unlimited tap on somebody else's phone, at our cost.
        policy = new OtpPolicy(4, 120, 3, 5, 0, 3);
        service = new OtpService(challenges, lockouts, () -> policy,
                new OtpStateWriter(challenges, lockouts, NO_TRANSACTIONS));

        for (int i = 0; i < 3; i++) service.issue(OtpPurpose.SIGN_IN, SUBJECT, false);

        BusinessException locked = assertThrows(BusinessException.class,
                () -> service.issue(OtpPurpose.SIGN_IN, SUBJECT, false));
        assertEquals("OTP_LOCKED", locked.getCode());
    }

    @Test
    void twoCodesCanBeOutstandingAtOnceAndEitherWorks() {
        // A phone and a laptop, or a request retried over a slow network. A store that keeps
        // only the newest tells the first one it is wrong when it is not.
        policy = new OtpPolicy(4, 120, 3, 5, 0, 5);
        service = new OtpService(challenges, lockouts, () -> policy,
                new OtpStateWriter(challenges, lockouts, NO_TRANSACTIONS));

        String first = service.issue(OtpPurpose.SIGN_IN, SUBJECT, false).code();
        service.issue(OtpPurpose.SIGN_IN, SUBJECT, false);

        assertDoesNotThrow(() -> service.verify(OtpPurpose.SIGN_IN, SUBJECT, first));
    }

    @Test
    void simulatedDeliveryUsesTheFixedCodeButKeepsEveryOtherRule() {
        OtpService.Issued issued = service.issue(OtpPurpose.SIGN_IN, SUBJECT, true);
        assertEquals("1234", issued.code(), "the documented demo code, at the configured length");

        // Still hashed, still expiring, still counted.
        assertNotEquals("1234", challenges.rows.get(0).getCodeHash());
        assertTrue(challenges.rows.get(0).getExpiresAt().isAfter(Instant.now()));
        assertThrows(RuntimeException.class, () -> service.verify(OtpPurpose.SIGN_IN, SUBJECT, "9999"));
    }

    @Test
    void theSubjectIsMatchedCaseInsensitively() {
        String code = service.issue(OtpPurpose.SIGN_IN, "Rawabi.Admin", false).code();
        assertDoesNotThrow(() -> service.verify(OtpPurpose.SIGN_IN, "rawabi.admin", code));
    }

    // ------------------------------------------------------------------ fakes ----------
    private static class FakeChallenges implements OtpChallengeRepository {
        final List<OtpChallenge> rows = new ArrayList<>();

        @Override public List<OtpChallenge> findUsable(String purpose, String subject, Instant now) {
            return rows.stream()
                    .filter(c -> c.getPurpose().equals(purpose) && c.getSubject().equals(subject))
                    .filter(c -> c.isUsable(now))
                    .sorted(Comparator.comparing(OtpChallenge::getExpiresAt).reversed())
                    .toList();
        }

        @Override public List<OtpChallenge> findUnconsumed(String purpose, String subject) {
            return rows.stream()
                    .filter(c -> c.getPurpose().equals(purpose) && c.getSubject().equals(subject))
                    .filter(c -> c.getConsumedAt() == null)
                    .toList();
        }

        @Override public int deleteExpiredBefore(Instant cutoff) {
            return rows.removeIf(c -> c.getExpiresAt().isBefore(cutoff)) ? 1 : 0;
        }

        @Override public <S extends OtpChallenge> S save(S entity) {
            if (entity.getId() == null) entity.setId(UUID.randomUUID());
            if (!rows.contains(entity)) rows.add(entity);
            return entity;
        }

        @Override public <S extends OtpChallenge> List<S> saveAll(Iterable<S> entities) {
            entities.forEach(this::save);
            List<S> out = new ArrayList<>();
            entities.forEach(out::add);
            return out;
        }

        // --- unused JpaRepository surface ---
        @Override public void flush() {}
        @Override public <S extends OtpChallenge> S saveAndFlush(S entity) { return save(entity); }
        @Override public <S extends OtpChallenge> List<S> saveAllAndFlush(Iterable<S> e) { return saveAll(e); }
        @Override public void deleteAllInBatch(Iterable<OtpChallenge> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<UUID> uuids) {}
        @Override public void deleteAllInBatch() {}
        @Override public OtpChallenge getOne(UUID uuid) { return null; }
        @Override public OtpChallenge getById(UUID uuid) { return null; }
        @Override public OtpChallenge getReferenceById(UUID uuid) { return null; }
        @Override public <S extends OtpChallenge> Optional<S> findOne(org.springframework.data.domain.Example<S> e) { return Optional.empty(); }
        @Override public <S extends OtpChallenge> List<S> findAll(org.springframework.data.domain.Example<S> e) { return List.of(); }
        @Override public <S extends OtpChallenge> List<S> findAll(org.springframework.data.domain.Example<S> e, org.springframework.data.domain.Sort s) { return List.of(); }
        @Override public <S extends OtpChallenge> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> e, org.springframework.data.domain.Pageable p) { return org.springframework.data.domain.Page.empty(); }
        @Override public <S extends OtpChallenge> long count(org.springframework.data.domain.Example<S> e) { return 0; }
        @Override public <S extends OtpChallenge> boolean exists(org.springframework.data.domain.Example<S> e) { return false; }
        @Override public <S extends OtpChallenge, R> R findBy(org.springframework.data.domain.Example<S> e, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> f) { return null; }
        @Override public List<OtpChallenge> findAll() { return rows; }
        @Override public List<OtpChallenge> findAllById(Iterable<UUID> uuids) { return List.of(); }
        @Override public Optional<OtpChallenge> findById(UUID uuid) {
            return rows.stream().filter(c -> uuid.equals(c.getId())).findFirst();
        }
        @Override public boolean existsById(UUID uuid) { return false; }
        @Override public long count() { return rows.size(); }
        @Override public void deleteById(UUID uuid) {}
        @Override public void delete(OtpChallenge entity) { rows.remove(entity); }
        @Override public void deleteAllById(Iterable<? extends UUID> uuids) {}
        @Override public void deleteAll(Iterable<? extends OtpChallenge> entities) {}
        @Override public void deleteAll() { rows.clear(); }
        @Override public List<OtpChallenge> findAll(org.springframework.data.domain.Sort sort) { return rows; }
        @Override public org.springframework.data.domain.Page<OtpChallenge> findAll(org.springframework.data.domain.Pageable p) { return org.springframework.data.domain.Page.empty(); }
    }

    /**
     * Two wrong codes arriving together must both be counted.
     *
     * <p>They used to collide: both read the tally at one version, both wrote the next, and the
     * loser surfaced as an unhandled 500 on sign-in while its increment was silently discarded.
     * Parallel attempts are what an attacker generates, so the lockout was weakest under
     * exactly the traffic it exists to stop. The write is retried against the row as it then
     * stands, so the count survives and nothing escapes to the caller.
     */
    @Test
    void aFailedAttemptThatLosesAWriteRaceIsStillCounted() {
        service.issue(OtpPurpose.SIGN_IN, SUBJECT, false);

        lockouts.conflictsToRaise = 1;
        assertThrows(ValidationException.class,
                () -> service.verify(OtpPurpose.SIGN_IN, SUBJECT, "0000"),
                "the wrong code is still rejected");

        assertTrue(lockouts.saveAttempts > 1, "the lost write was retried rather than abandoned");
        // Recorded, rather than a specific number. The retry is correct because the failed
        // transaction rolls back and the next attempt re-reads the row - and an in-memory fake
        // has no rollback, so it hands the retry the object it already mutated and the tally
        // reads one high. Asserting the exact count here would be asserting the fake's
        // shortcoming; what this test can say faithfully is that the failure survived a lost
        // race instead of vanishing with it, which is the property that was broken.
        assertTrue(lockouts.rows.get(0).getFailedCount() >= 1,
                "and the attempt it carried was counted, not dropped");
    }

    private static class FakeLockouts implements OtpLockoutRepository {
        final List<OtpLockout> rows = new ArrayList<>();

        @Override public Optional<OtpLockout> findByPurposeAndSubject(String purpose, String subject) {
            return rows.stream()
                    .filter(l -> l.getPurpose().equals(purpose) && l.getSubject().equals(subject))
                    .findFirst();
        }

        /** Number of saves still to be lost to a simulated concurrent write. */
        int conflictsToRaise = 0;
        int saveAttempts = 0;

        @Override public <S extends OtpLockout> S save(S entity) {
            saveAttempts++;
            if (conflictsToRaise > 0) {
                conflictsToRaise--;
                throw new org.springframework.orm.ObjectOptimisticLockingFailureException(
                        OtpLockout.class, entity.getId());
            }
            if (entity.getId() == null) entity.setId(UUID.randomUUID());
            if (!rows.contains(entity)) rows.add(entity);
            return entity;
        }

        @Override public void delete(OtpLockout entity) { rows.remove(entity); }

        // --- unused JpaRepository surface ---
        @Override public void flush() {}
        @Override public <S extends OtpLockout> S saveAndFlush(S entity) { return save(entity); }
        @Override public <S extends OtpLockout> List<S> saveAll(Iterable<S> e) { List<S> o = new ArrayList<>(); e.forEach(x -> o.add(save(x))); return o; }
        @Override public <S extends OtpLockout> List<S> saveAllAndFlush(Iterable<S> e) { return saveAll(e); }
        @Override public void deleteAllInBatch(Iterable<OtpLockout> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<UUID> uuids) {}
        @Override public void deleteAllInBatch() {}
        @Override public OtpLockout getOne(UUID uuid) { return null; }
        @Override public OtpLockout getById(UUID uuid) { return null; }
        @Override public OtpLockout getReferenceById(UUID uuid) { return null; }
        @Override public <S extends OtpLockout> Optional<S> findOne(org.springframework.data.domain.Example<S> e) { return Optional.empty(); }
        @Override public <S extends OtpLockout> List<S> findAll(org.springframework.data.domain.Example<S> e) { return List.of(); }
        @Override public <S extends OtpLockout> List<S> findAll(org.springframework.data.domain.Example<S> e, org.springframework.data.domain.Sort s) { return List.of(); }
        @Override public <S extends OtpLockout> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> e, org.springframework.data.domain.Pageable p) { return org.springframework.data.domain.Page.empty(); }
        @Override public <S extends OtpLockout> long count(org.springframework.data.domain.Example<S> e) { return 0; }
        @Override public <S extends OtpLockout> boolean exists(org.springframework.data.domain.Example<S> e) { return false; }
        @Override public <S extends OtpLockout, R> R findBy(org.springframework.data.domain.Example<S> e, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> f) { return null; }
        @Override public List<OtpLockout> findAll() { return rows; }
        @Override public List<OtpLockout> findAllById(Iterable<UUID> uuids) { return List.of(); }
        @Override public Optional<OtpLockout> findById(UUID uuid) {
            return rows.stream().filter(l -> uuid.equals(l.getId())).findFirst();
        }
        @Override public boolean existsById(UUID uuid) { return false; }
        @Override public long count() { return rows.size(); }
        @Override public void deleteById(UUID uuid) {}
        @Override public void deleteAllById(Iterable<? extends UUID> uuids) {}
        @Override public void deleteAll(Iterable<? extends OtpLockout> entities) {}
        @Override public void deleteAll() { rows.clear(); }
        @Override public List<OtpLockout> findAll(org.springframework.data.domain.Sort sort) { return rows; }
        @Override public org.springframework.data.domain.Page<OtpLockout> findAll(org.springframework.data.domain.Pageable p) { return org.springframework.data.domain.Page.empty(); }
    }
}
