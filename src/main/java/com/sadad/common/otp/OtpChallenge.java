package com.sadad.common.otp;

import com.sadad.common.persistence.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

import java.time.Instant;

/**
 * One issued code, and everything needed to judge a submission against it.
 *
 * <p>The code itself is not here. {@link #codeHash} is a salted SHA-256, so reading this
 * table answers "is this the code" and never "what is the code" - which matters because the
 * people most likely to read it are operators doing support, and a code they can read is a
 * code they can use.
 */
@Entity
@Table(name = "otp_challenges")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtpChallenge extends BaseAuditableEntity {

    @Column(name = "purpose", nullable = false)
    private String purpose;

    @Column(name = "subject", nullable = false)
    private String subject;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(name = "code_salt", nullable = false)
    private String codeSalt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "attempts", nullable = false)
    @Builder.Default
    private int attempts = 0;

    /** Set the moment a code is accepted, so the same code cannot be used twice. */
    @Column(name = "consumed_at")
    private Instant consumedAt;

    public boolean isUsable(Instant now) {
        return consumedAt == null && expiresAt.isAfter(now);
    }
}
