package com.sadad.common.integration;

import com.sadad.common.persistence.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

/**
 * Own mapping of the {@code integration_provider_configs} table saddad-auth's/
 * saddad-wallet's/saddad-violations' own copies also map (read-only there, for their
 * real provider callers -
 * SadadBillProvider, WathqEntityValidationProvider, etc.). This service owns the admin CRUD.
 */
@Entity
@Table(name = "integration_provider_configs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntegrationProviderConfig extends BaseAuditableEntity {

    @Column(name = "provider_id", unique = true, nullable = false)
    private String providerId;

    @Column(name = "authority", nullable = false)
    private String authority;

    @Column(name = "name_en", nullable = false)
    private String nameEn;

    @Column(name = "name_ar", nullable = false)
    private String nameAr;

    @Column(name = "mode", nullable = false)
    @Builder.Default
    private String mode = "MOCK"; // MOCK, SANDBOX, LIVE

    @Column(name = "status", nullable = false)
    @Builder.Default
    private String status = "UP"; // UP, DEGRADED, DOWN

    @Column(name = "latency_ms", nullable = false)
    private int latencyMs;

    @Column(name = "circuit_breaker", nullable = false)
    @Builder.Default
    private String circuitBreaker = "CLOSED"; // CLOSED, HALF_OPEN, OPEN

    /**
     * The vendor credential for this provider, encrypted at rest.
     *
     * <p>Never leaves the platform in readable form and is never returned by a read API:
     * the console is told only whether one is set. A credential that can be read back out
     * of a console is a credential that leaks through a screenshot.
     */
    @Column(name = "credential_encrypted")
    private String credentialEncrypted;

    @Column(name = "credential_updated_at")
    private java.time.Instant credentialUpdatedAt;

    /** What this credential is, in the operator's words - "Wathq subscription key". */
    @Column(name = "credential_label")
    private String credentialLabel;

    /**
     * The gateway's PUBLIC/publishable key, sent to the browser to initialise its hosted
     * payment form.
     *
     * <p>Deliberately not encrypted and deliberately not in {@link #credentialEncrypted}.
     * A publishable key is designed to be public - it ends up in a page anybody can view the
     * source of, and it cannot move money on its own. Hiding it behind the control that
     * protects real secrets would make the real secret harder to reason about. It is what
     * lets a card be typed into the gateway's own form rather than ours, which is why this
     * platform never sees a card number.
     */
    @Column(name = "publishable_key")
    private String publishableKey;

    @Column(name = "module", nullable = false)
    @Builder.Default
    private String module = "GENERAL";
}
