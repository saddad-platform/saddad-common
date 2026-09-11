package com.sadad.common.otp;

import com.sadad.common.persistence.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

import java.time.Instant;

/**
 * The running tally for one subject and purpose: failures towards a lock, and sends towards
 * the flood ceiling.
 *
 * <p>Separate from {@link OtpChallenge} because it has to outlive the challenge that caused
 * it. If the count lived on the challenge, requesting a fresh code would reset the failure
 * tally, and "guess three times, request a new code, guess three more" would be unlimited
 * guessing with extra steps.
 *
 * <p>In the database rather than in memory, unlike the in-process guards this replaces. A
 * lock held only in a JVM's heap is released by a restart and is invisible to a second
 * instance - so the control that exists to stop an attacker is precisely the one that stops
 * working when the platform is under load or being deployed.
 */
@Entity
@Table(name = "otp_lockouts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtpLockout extends BaseAuditableEntity {

    @Column(name = "purpose", nullable = false)
    private String purpose;

    @Column(name = "subject", nullable = false)
    private String subject;

    @Column(name = "failed_count", nullable = false)
    @Builder.Default
    private int failedCount = 0;

    /** Null while merely accumulating failures; set once locked. */
    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "window_started_at", nullable = false)
    @Builder.Default
    private Instant windowStartedAt = Instant.now();

    @Column(name = "sends_in_window", nullable = false)
    @Builder.Default
    private int sendsInWindow = 0;

    @Column(name = "last_sent_at")
    private Instant lastSentAt;

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }
}
