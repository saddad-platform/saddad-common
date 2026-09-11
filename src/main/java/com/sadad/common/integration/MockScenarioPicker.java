package com.sadad.common.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What an upstream "returns" while a provider is simulated.
 *
 * <p><b>One copy, deliberately.</b> This class existed four times over - in saddad-auth,
 * saddad-wallet, saddad-violations and saddad-admin - byte-identical apart from import order.
 * saddad-onboarding had no copy at all, which is why the mock scenarios an operator
 * configured in the console were simply never consulted by the onboarding wizard: its
 * providers decided simulated outcomes from a hash of the applicant's own details instead,
 * so roughly one applicant in ten was refused by arithmetic nobody could see or change.
 *
 * <p>The pinned default is what a simulated call returns. Falling back to a random scenario
 * when none is pinned is deliberate for exploratory testing, but every endpoint should have
 * a default - see {@code V45__mock_scenarios_for_every_endpoint.sql}, which pins the
 * successful outcome for all of them, because the common case for a simulated platform is
 * that it should work.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MockScenarioPicker {

    private final IntegrationMockScenarioRepository scenarioRepository;
    private final ObjectMapper objectMapper;
    private final SecureRandom random = new SecureRandom();

    /** The scenario body for this endpoint, or an empty map when none is configured. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> pick(UUID endpointId) {
        List<IntegrationMockScenario> scenarios = scenarioRepository.findAllByEndpointId(endpointId);
        if (scenarios.isEmpty()) return Map.of();

        IntegrationMockScenario chosen = scenarios.stream()
                .filter(IntegrationMockScenario::isDefault)
                .findFirst()
                .orElseGet(() -> scenarios.get(random.nextInt(scenarios.size())));

        try {
            return objectMapper.readValue(chosen.getResponseJson(), Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid mock scenario JSON for endpoint " + endpointId
                    + " (scenario \"" + chosen.getScenarioName() + "\"): " + e.getMessage(), e);
        }
    }

    /**
     * The same, but never throwing.
     *
     * <p>For the callers on a customer-facing path: a scenario somebody mistyped in the
     * console must not take onboarding down, it must fall back to the provider's built-in
     * behaviour.
     */
    public Map<String, Object> pickQuietly(UUID endpointId) {
        try {
            return pick(endpointId);
        } catch (RuntimeException e) {
            log.warn("Could not read a mock scenario for endpoint {} - using the built-in simulation: {}",
                    endpointId, e.getMessage());
            return Map.of();
        }
    }
}
