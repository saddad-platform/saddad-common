package com.sadad.common.integration;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IntegrationProviderConfigRepository extends JpaRepository<IntegrationProviderConfig, UUID> {
    List<IntegrationProviderConfig> findAllByOrderByNameEnAsc();
    Optional<IntegrationProviderConfig> findByProviderIdIgnoreCase(String providerId);
}
