package com.sadad.common.integration;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IntegrationEndpointRepository extends JpaRepository<IntegrationEndpoint, UUID> {
    List<IntegrationEndpoint> findAllByProviderId(UUID providerId);
    Optional<IntegrationEndpoint> findByProviderIdAndEndpointKey(UUID providerId, String endpointKey);
}
