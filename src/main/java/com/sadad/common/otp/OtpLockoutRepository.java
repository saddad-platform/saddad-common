package com.sadad.common.otp;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OtpLockoutRepository extends JpaRepository<OtpLockout, UUID> {
    Optional<OtpLockout> findByPurposeAndSubject(String purpose, String subject);
}
