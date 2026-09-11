package com.sadad.common.otp;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SmsOtpConfigRepository extends JpaRepository<SmsOtpConfig, UUID> {

    /** The one row. Returns defaults rather than throwing if it is somehow absent, because
     * a missing configuration row must not be able to stop everybody signing in. */
    default SmsOtpConfig getSingleton() {
        return findAll().stream().findFirst().orElseGet(() -> SmsOtpConfig.builder().build());
    }
}
