package com.sadad.common.otp;

/**
 * Where the current policy comes from.
 *
 * <p>An interface with one production implementation, so that {@link OtpService} - which is
 * where all of the security decisions live - can be exercised against a policy handed
 * straight to it. Testing lockout thresholds by writing configuration rows would test the
 * database rather than the decisions.
 */
@FunctionalInterface
public interface OtpPolicyProvider {
    OtpPolicy current();
}
