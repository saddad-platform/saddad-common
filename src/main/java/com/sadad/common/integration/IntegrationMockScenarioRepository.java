package com.sadad.common.integration;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IntegrationMockScenarioRepository extends JpaRepository<IntegrationMockScenario, UUID> {
    List<IntegrationMockScenario> findAllByEndpointId(UUID endpointId);
}
