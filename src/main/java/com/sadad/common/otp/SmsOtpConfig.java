package com.sadad.common.otp;

import com.sadad.common.persistence.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

/**
 * The single row an administrator edits to govern one-time codes everywhere.
 *
 * <p>One mapping, in the module every service already scans. There were two - saddad-admin's
 * for the console's CRUD and saddad-auth's read-only copy - and the fields added here would
 * otherwise have had to be added to both, correctly, twice.
 */
@Entity
@Table(name = "sms_otp_config")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmsOtpConfig extends BaseAuditableEntity {

    @Column(name = "otp_length", nullable = false)
    @Builder.Default
    private int otpLength = 4;

    @Column(name = "sms_template")
    @Builder.Default
    private String smsTemplate = "Your SADAD sign-in code is: ${code}";

    /** Seconds - the unit the countdown in front of the person is counting. */
    @Column(name = "expire_seconds", nullable = false)
    @Builder.Default
    private int expireSeconds = 120;

    /** Wrong codes tolerated. The attempt after this many failures is the one that locks. */
    @Column(name = "trials_limit", nullable = false)
    @Builder.Default
    private int trialsLimit = 3;

    @Column(name = "trials_reset_minutes", nullable = false)
    @Builder.Default
    private int trialsResetMinutes = 5;

    @Column(name = "resend_cooldown_seconds", nullable = false)
    @Builder.Default
    private int resendCooldownSeconds = 30;

    @Column(name = "max_sends_per_window", nullable = false)
    @Builder.Default
    private int maxSendsPerWindow = 5;

    public OtpPolicy toPolicy() {
        return new OtpPolicy(otpLength, expireSeconds, trialsLimit, trialsResetMinutes,
                resendCooldownSeconds, maxSendsPerWindow);
    }
}
