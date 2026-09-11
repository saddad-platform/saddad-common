package com.sadad.common.otp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Reads the policy an administrator set, and never fails.
 *
 * <p>A database hiccup while reading configuration must not become "nobody can sign in", so
 * an unreadable row falls back to a strict default rather than propagating. Same fail-safe
 * stance as {@code ConfiguredEndpoints} takes for integration contracts, for the same
 * reason: configuration is an input to the decision, not the decision itself.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseOtpPolicyProvider implements OtpPolicyProvider {

    private final SmsOtpConfigRepository repository;

    @Override
    public OtpPolicy current() {
        try {
            return repository.getSingleton().toPolicy();
        } catch (RuntimeException e) {
            log.warn("Could not read the OTP policy - using the built-in default: {}", e.getMessage());
            return OtpPolicy.fallback();
        }
    }
}
