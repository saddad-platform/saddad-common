package com.sadad.common.security.introspection;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Live call to the token-issuing service's own {@code POST /v1/internal/auth/introspect}
 * (guarded by the same {@code X-Internal-Api-Key} trust boundary every other internal
 * endpoint in this codebase already uses) - catches a revoked/superseded session the
 * token's own signature and expiry can never reveal on their own. Only registered when
 * {@code sadad.introspection.online.base-url} is actually configured, so services that
 * never opt into ONLINE mode don't pay for an unused bean or a dangling HTTP client
 * pointed at nothing.
 *
 * Fails closed (returns inactive) on any connectivity problem: unlike the fire-and-forget
 * internal calls elsewhere in this codebase (mail send, notification create), a caller
 * that deliberately asked for ONLINE mode is asking "is this session still genuinely
 * valid right now" for a revocation-sensitive decision - silently treating "auth service
 * unreachable" as "still valid" would defeat the entire point of asking online in the
 * first place.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "sadad.introspection.online", name = "base-url")
public class OnlineTokenIntrospector implements TokenIntrospector {

    private final RestClient restClient;

    public OnlineTokenIntrospector(
            @Value("${sadad.introspection.online.base-url}") String baseUrl,
            @Value("${sadad.introspection.online.api-key}") String internalApiKey) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3_000);
        requestFactory.setReadTimeout(5_000);

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Internal-Api-Key", internalApiKey)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public IntrospectionResult introspect(String token) {
        try {
            IntrospectionWireDtos.IntrospectResponse response = restClient.post()
                    .uri("/v1/internal/auth/introspect")
                    .body(new IntrospectionWireDtos.IntrospectRequest(token))
                    .retrieve()
                    .body(new ParameterizedTypeReference<com.sadad.common.core.api.ApiResponse<IntrospectionWireDtos.IntrospectResponse>>() {})
                    .getData();

            if (response == null || !response.isActive()) {
                return IntrospectionResult.inactive(IntrospectionSource.ONLINE);
            }
            return new IntrospectionResult(
                    true,
                    response.getSubject(),
                    response.getTenantId(),
                    response.getCrNumber(),
                    response.getRoles(),
                    response.getSessionId(),
                    response.getExpiresAt(),
                    IntrospectionSource.ONLINE
            );
        } catch (RestClientException | NullPointerException e) {
            log.warn("[ONLINE-INTROSPECTION] Call failed, treating token as inactive (fail-closed): {}", e.getMessage());
            return IntrospectionResult.inactive(IntrospectionSource.ONLINE);
        }
    }

    @Override
    public IntrospectionSource source() {
        return IntrospectionSource.ONLINE;
    }
}
