package com.sadad.common.integration;

import com.sadad.common.persistence.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

import java.util.UUID;

/**
 * Own mapping of the {@code integration_mock_scenarios} table saddad-auth's/saddad-wallet's/
 * saddad-violations' own copies also map (read-only there, via their own
 * MockScenarioPicker). This service owns the admin CRUD.
 */
@Entity
@Table(name = "integration_mock_scenarios")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntegrationMockScenario extends BaseAuditableEntity {

    @Column(name = "endpoint_id", nullable = false)
    private UUID endpointId;

    @Column(name = "scenario_name", nullable = false)
    private String scenarioName;

    @Column(name = "response_json", nullable = false)
    private String responseJson;

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private boolean isDefault = false;
}
