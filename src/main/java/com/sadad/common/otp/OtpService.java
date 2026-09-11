package com.sadad.common.otp;

import com.sadad.common.errors.ErrorCode;
import com.sadad.common.exception.BusinessException;
import com.sadad.common.exception.ValidationException;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Issuing and checking one-time codes, for every flow on the platform.
 *
 * <p><b>One implementation, deliberately.</b> Sign-in, password reset and the onboarding
 * mobile check each had their own arrangement, and the differences between them were not
 * decisions - they were drift. Expiry existed in one place and not another; nothing counted
 * a wrong guess anywhere; each returned the code it had just issued to the caller so the
 * browser could display it. A one-time code is a security control, and a security control
 * implemented three times is implemented badly at least twice.
 *
 * <p><b>What this guarantees.</b>
 * <ul>
 *   <li>The code is random, from {@link SecureRandom}, and never stored - only a salted
 *       SHA-256 of it. Reading the database cannot tell you a code.</li>
 *   <li>It expires, on a schedule an administrator sets.</li>
 *   <li>Wrong guesses are counted across challenges, so requesting a fresh code does not
 *       reset the tally. Past the limit the subject is locked for a configured period.</li>
 *   <li>Comparison is constant-time, so the failure cannot be walked digit by digit.</li>
 *   <li>Sends are rate-limited twice over - a minimum gap between them, and a ceiling per
 *       window - so "resend" is not an unlimited tap on somebody else's phone.</li>
 *   <li>A code is consumed on success and cannot be replayed.</li>
 * </ul>
 *
 * <p><b>Simulated delivery.</b> With no SMS gateway configured there is nobody to receive a
 * random code, so in that mode the code is the policy's fixed demo value. Everything else
 * still applies - it expires, it is counted, it locks - and it is never returned by an API.
 * A platform that cannot be demonstrated gets a back door added to it eventually; one that
 * can be demonstrated safely does not.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    /** What a simulated send "delivers". Documented in the seed-data guide, never returned. */
    public static final String SIMULATED_CODE_DIGITS = "1234567890";

    private final OtpChallengeRepository challengeRepository;
    private final OtpLockoutRepository lockoutRepository;
    private final OtpPolicyProvider policyProvider;
    /** Counters are written through this so that rejecting an attempt does not roll back the
     * record of it - see {@link OtpStateWriter}. */
    private final OtpStateWriter stateWriter;
    private final SecureRandom random = new SecureRandom();

    /**
     * A freshly issued challenge.
     *
     * <p>{@link #code} is for the caller to hand to an SMS provider. It must never reach an
     * HTTP response: that was the previous design, and it meant anyone who could call the
     * initiate endpoint could sign in without the phone.
     */
    @Builder
    public record Issued(String code, int length, Instant expiresAt, int expiresInSeconds,
                         int resendAvailableInSeconds) {}

    /**
     * Issues a code, or refuses because the subject is locked or has asked too often.
     *
     * @param simulated when true the code is the fixed demo value rather than a random one
     */
    public Issued issue(String purpose, String rawSubject, boolean simulated) {
        String subject = normalize(rawSubject);
        Instant now = Instant.now();
        OtpPolicy policy = policyProvider.current();

        OtpLockout lockout = stateWriter.snapshot(purpose, subject, policy, now);
        assertNotLocked(lockout, now);

        // Two independent limits. The cooldown stops a held-down button; the window ceiling
        // stops a patient script that waits out the cooldown every time.
        if (lockout.getLastSentAt() != null) {
            long sinceLast = Duration.between(lockout.getLastSentAt(), now).toSeconds();
            if (sinceLast < policy.resendCooldownSeconds()) {
                throw new BusinessException(ErrorCode.OTP_RESEND_TOO_SOON,
                        Map.of("seconds", policy.resendCooldownSeconds() - sinceLast));
            }
        }
        if (lockout.getSendsInWindow() >= policy.maxSendsPerWindow()) {
            // Treated as a lock rather than a softer refusal: at this point the traffic is
            // no longer somebody mistyping their own code.
            stateWriter.lock(purpose, subject, policy, now);
            log.warn("[OTP] send ceiling reached for purpose={} - locking", purpose);
            throw new BusinessException(ErrorCode.OTP_LOCKED,
                    Map.of("minutes", policy.trialsResetMinutes()));
        }

        String code = simulated
                ? SIMULATED_CODE_DIGITS.substring(0, Math.min(policy.length(), SIMULATED_CODE_DIGITS.length()))
                : randomCode(policy.length());

        String salt = randomSalt();
        Instant expiresAt = now.plusSeconds(policy.expireSeconds());
        stateWriter.registerSend(purpose, subject, OtpChallenge.builder()
                .purpose(purpose)
                .subject(subject)
                .codeHash(hash(code, salt))
                .codeSalt(salt)
                .expiresAt(expiresAt)
                .attempts(0)
                .build(), policy, now);

        // Never the code, and never the subject in full - both end up in log aggregators.
        log.info("[OTP] issued purpose={} expiresIn={}s simulated={}",
                purpose, policy.expireSeconds(), simulated);

        return Issued.builder()
                .code(code)
                .length(policy.length())
                .expiresAt(expiresAt)
                .expiresInSeconds(policy.expireSeconds())
                .resendAvailableInSeconds(policy.resendCooldownSeconds())
                .build();
    }

    /**
     * Checks a submitted code, consuming it on success.
     *
     * <p>Throws rather than returning false, because every caller's correct response to a
     * failure is to stop - and a boolean invites a caller to carry on past it.
     */
    public void verify(String purpose, String rawSubject, String submitted) {
        String subject = normalize(rawSubject);
        Instant now = Instant.now();
        OtpPolicy policy = policyProvider.current();

        OtpLockout lockout = stateWriter.snapshot(purpose, subject, policy, now);
        assertNotLocked(lockout, now);

        List<OtpChallenge> usable = challengeRepository.findUsable(purpose, subject, now);
        if (usable.isEmpty()) {
            // Distinguish "your code ran out" from "that code is wrong". The first is the
            // platform's doing and has an obvious remedy; telling somebody their correct
            // code is wrong sends them looking for a problem that is not there.
            boolean hadOne = !challengeRepository.findUnconsumed(purpose, subject).isEmpty();
            registerFailure(purpose, subject, List.of(), policy, now);
            throw new ValidationException(hadOne ? ErrorCode.OTP_EXPIRED : ErrorCode.AUTH_OTP_INVALID);
        }

        for (OtpChallenge challenge : usable) {
            if (constantTimeEquals(challenge.getCodeHash(), hash(submitted, challenge.getCodeSalt()))) {
                // A success clears the slate, including the send ceiling: the person has
                // demonstrably got their phone, so they are not the flood this guards against.
                stateWriter.consume(challenge.getId(), purpose, subject, now);
                return;
            }
        }

        registerFailure(purpose, subject,
                usable.stream().map(OtpChallenge::getId).toList(), policy, now);
        throw new ValidationException(ErrorCode.AUTH_OTP_INVALID);
    }

    /** Whether this subject is currently locked, without recording anything. */
    @Transactional(readOnly = true)
    public boolean isLocked(String purpose, String rawSubject) {
        return lockoutRepository.findByPurposeAndSubject(purpose, normalize(rawSubject))
                .map(l -> l.isLocked(Instant.now()))
                .orElse(false);
    }

    /** Seconds remaining on a lock, or zero. For telling somebody when to come back. */
    @Transactional(readOnly = true)
    public long lockedForSeconds(String purpose, String rawSubject) {
        Instant now = Instant.now();
        return lockoutRepository.findByPurposeAndSubject(purpose, normalize(rawSubject))
                .filter(l -> l.isLocked(now))
                .map(l -> Duration.between(now, l.getLockedUntil()).toSeconds())
                .orElse(0L);
    }

    /**
     * Clears expired rows. Runs in its own transaction so housekeeping can never roll back
     * a sign-in that happened to trigger it.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int purgeExpired() {
        return challengeRepository.deleteExpiredBefore(Instant.now().minus(Duration.ofHours(1)));
    }

    // ---------------------------------------------------------------------------------

    private void assertNotLocked(OtpLockout lockout, Instant now) {
        if (lockout.isLocked(now)) {
            long minutes = Math.max(1, Duration.between(now, lockout.getLockedUntil()).toMinutes() + 1);
            throw new BusinessException(ErrorCode.OTP_LOCKED, Map.of("minutes", minutes));
        }
    }

    /**
     * Counts the failure, and reports the lock if this was the attempt that caused one.
     *
     * <p>The counting is committed separately, because the caller's next act is to throw and
     * that would otherwise roll the count back - which is precisely the bug that let somebody
     * guess without limit.
     */
    private void registerFailure(String purpose, String subject, List<java.util.UUID> attemptedIds,
                                 OtpPolicy policy, Instant now) {
        Instant lockedUntil = stateWriter.registerFailure(purpose, subject, attemptedIds, policy, now);
        if (lockedUntil != null && lockedUntil.isAfter(now)) {
            log.warn("[OTP] locked purpose={} for {} minutes", purpose, policy.trialsResetMinutes());
            throw new BusinessException(ErrorCode.OTP_LOCKED,
                    Map.of("minutes", policy.trialsResetMinutes()));
        }
    }

    private String randomCode(int length) {
        StringBuilder code = new StringBuilder(length);
        for (int i = 0; i < length; i++) code.append(random.nextInt(10));
        return code.toString();
    }

    private String randomSalt() {
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    private String hash(String code, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(Base64.getDecoder().decode(salt));
            return Base64.getEncoder().encodeToString(
                    digest.digest(code == null ? new byte[0] : code.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every JVM this runs on", e);
        }
    }

    /** Length-independent and value-independent: a comparison that returns early tells an
     * attacker how much of their guess was right. */
    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private String normalize(String subject) {
        return subject == null ? "" : subject.trim().toLowerCase();
    }
}
