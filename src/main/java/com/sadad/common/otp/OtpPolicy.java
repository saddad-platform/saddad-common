package com.sadad.common.otp;

/**
 * The numbers that govern how a one-time code behaves, as an administrator set them.
 *
 * <p>All of it is configuration rather than code because these are exactly the values that
 * get tuned in response to something real - a vendor's SMS latency, a fraud pattern, a
 * support queue full of people locked out too eagerly - and none of them is worth a release.
 *
 * @param length              digits in the code
 * @param expireSeconds       how long it stays valid
 * @param trialsLimit         wrong codes tolerated before locking; the attempt *after* this
 *                            many failures is the one that locks
 * @param trialsResetMinutes  how long the lock lasts
 * @param resendCooldownSeconds smallest gap between two sends to the same subject
 * @param maxSendsPerWindow   ceiling on sends within one window, so waiting out the cooldown
 *                            repeatedly is not an unlimited SMS tap
 */
public record OtpPolicy(int length, int expireSeconds, int trialsLimit, int trialsResetMinutes,
                        int resendCooldownSeconds, int maxSendsPerWindow) {

    /** Used when no configuration row can be read - deliberately strict rather than lax. */
    public static OtpPolicy fallback() {
        return new OtpPolicy(4, 120, 3, 5, 30, 5);
    }

    public OtpPolicy {
        // Clamped rather than validated-and-thrown: a policy row edited to something absurd
        // must not take sign-in down for everybody.
        length = clamp(length, 4, 10);
        expireSeconds = clamp(expireSeconds, 30, 900);
        trialsLimit = clamp(trialsLimit, 1, 10);
        trialsResetMinutes = clamp(trialsResetMinutes, 1, 1440);
        resendCooldownSeconds = clamp(resendCooldownSeconds, 0, 600);
        maxSendsPerWindow = clamp(maxSendsPerWindow, 1, 50);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
