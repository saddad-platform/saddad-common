package com.sadad.common.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sadad.common.errors.ErrorCode;
import com.sadad.common.exception.IntegrationException;
import com.sadad.common.secrets.IntegrationSecretCrypto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * Fires a real HTTP call built entirely from an admin-configured {@link IntegrationEndpoint}
 * - URL per mode, method, headers, query params and now the vendor credential are all data,
 * not code.
 *
 * <p><b>One copy, deliberately.</b> This class existed four times over - in saddad-auth,
 * saddad-wallet, saddad-violations and saddad-admin - byte-identical apart from its own
 * javadoc explaining that duplicating a small technical utility was preferable to sharing
 * it. That reasoning did not survive contact with a behaviour change: adding credential
 * injection meant making the same edit in four places, and the platform had already been
 * bitten once by an edit that reached three of four call sites (the MOCK/SIMULATED spelling,
 * which would have sent a live billed request). A utility that every service must change
 * together is not four utilities.
 *
 * <p>Never treats an HTTP error as a passing result - {@link #call} always throws,
 * fail-closed, because a wrong "success" here can gate a real payment or an account
 * provisioning. {@link #probe} is the deliberate exception and exists only for the console's
 * "Test call", where the operator needs to see the 401 rather than be protected from it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DynamicHttpCaller {

    private final ObjectMapper objectMapper;
    private final IntegrationProviderConfigRepository providerRepository;
    /** Absent in a service with no sadad.secrets.encryption-key-base64; a stored credential
     * is then simply not applied rather than the service failing to start. */
    private final ObjectProvider<IntegrationSecretCrypto> secretCrypto;

    /**
     * The credential an operator has stored for the provider that owns this endpoint.
     *
     * <p>Looked up here rather than passed in, so that every existing call site gains the
     * behaviour without changing: the alternative was threading a credential through six
     * signatures across four services, which is how call sites get missed.
     *
     * <p>Never logged, and never thrown in a message.
     */
    private String credentialFor(IntegrationEndpoint endpoint) {
        try {
            IntegrationSecretCrypto crypto = secretCrypto.getIfAvailable();
            if (crypto == null || endpoint.getProviderId() == null) return null;
            return providerRepository.findById(endpoint.getProviderId())
                    .map(IntegrationProviderConfig::getCredentialEncrypted)
                    .filter(value -> value != null && !value.isBlank())
                    .map(crypto::decrypt)
                    .orElse(null);
        } catch (RuntimeException e) {
            log.warn("Could not read the stored credential for endpoint {} - calling without it: {}",
                    endpoint.getNameEn(), e.getMessage());
            return null;
        }
    }

    /** Adds the stored credential to the header this endpoint names for it. Kept out of
     * headers_json because that column is plain text and is returned to the console. */
    private void applyCredential(RestClient.Builder builder, IntegrationEndpoint endpoint, String credential) {
        if (credential != null && !credential.isBlank()
                && endpoint.getSecretHeaderName() != null && !endpoint.getSecretHeaderName().isBlank()) {
            builder.defaultHeader(endpoint.getSecretHeaderName(), credential);
        }
    }

    public Map<String, Object> call(IntegrationEndpoint endpoint, String mode,
                                     Map<String, String> pathVariables, Map<String, String> extraQueryParams) {
        return call(endpoint, mode, pathVariables, extraQueryParams, null);
    }

    /**
     * What one call to an endpoint actually did: the status, the body, the URL and how long
     * it took.
     *
     * <p>{@link #call} is fail-closed on purpose - a business path must never mistake a 500
     * for an answer, so it throws and the status is lost. The console's "Test call" needs
     * the opposite: an operator diagnosing a misconfigured endpoint needs to see the 401 and
     * the body that came with it. So this reports instead of throwing, and nothing but the
     * console uses it.
     */
    public record Probe(boolean success, Integer status, String statusText,
                        Map<String, Object> body, String rawBody, String errorMessage,
                        String url, String method, long durationMs) {}

    public Probe probe(IntegrationEndpoint endpoint, String mode) {
        long startedAt = System.nanoTime();
        String url = resolveUrl(endpoint, mode);
        String method = endpoint.getHttpMethod() == null ? "GET" : endpoint.getHttpMethod().toUpperCase();
        if (url == null || url.isBlank()) {
            return new Probe(false, null, null, null, null,
                    "No " + mode + " URL is configured for this endpoint - set one before testing it.",
                    null, method, elapsedMs(startedAt));
        }

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(url);
        readJsonMap(endpoint.getQueryParamsJson()).forEach(uriBuilder::queryParam);
        java.net.URI uri;
        try {
            uri = uriBuilder.build().encode().toUri();
        } catch (RuntimeException e) {
            // A URL with an unsubstituted {placeholder} in it lands here. Saying so is far
            // more useful than "call failed".
            return new Probe(false, null, null, null, null,
                    "The configured URL could not be used as an address: " + e.getMessage(),
                    url, method, elapsedMs(startedAt));
        }

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(endpoint.getTimeoutMs());
        requestFactory.setReadTimeout(endpoint.getTimeoutMs());
        RestClient.Builder builder = RestClient.builder().requestFactory(requestFactory);
        readJsonMap(endpoint.getHeadersJson()).forEach((k, v) -> {
            if (v != null && !v.isBlank()) builder.defaultHeader(k, v);
        });
        applyCredential(builder, endpoint, credentialFor(endpoint));

        try {
            // No retries here: a test call reports one attempt, because an operator wants to
            // see what happened, not an outcome averaged over several tries.
            org.springframework.http.ResponseEntity<String> response = builder.build()
                    .method(HttpMethod.valueOf(method))
                    .uri(uri)
                    // Every status is a result to report, so none of them is an exception.
                    .exchange((request, clientResponse) -> org.springframework.http.ResponseEntity
                            .status(clientResponse.getStatusCode())
                            .body(new String(clientResponse.getBody().readAllBytes(),
                                    java.nio.charset.StandardCharsets.UTF_8)), false);

            int status = response.getStatusCode().value();
            String raw = response.getBody();
            Map<String, Object> parsed = null;
            try {
                if (raw != null && !raw.isBlank()) {
                    parsed = objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {});
                }
            } catch (Exception ignored) {
                // Not JSON - an HTML error page, or a bare string. rawBody carries it.
            }
            return new Probe(response.getStatusCode().is2xxSuccessful(), status, reasonFor(status),
                    parsed, parsed == null ? raw : null, null, uri.toString(), method, elapsedMs(startedAt));
        } catch (Exception e) {
            // No response at all: unknown host, refused connection, timeout.
            return new Probe(false, null, null, null, null, e.getMessage(),
                    uri.toString(), method, elapsedMs(startedAt));
        }
    }

    private long elapsedMs(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000;
    }

    private String reasonFor(int status) {
        try {
            return org.springframework.http.HttpStatus.valueOf(status).getReasonPhrase();
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    public Map<String, Object> call(IntegrationEndpoint endpoint, String mode,
                                     Map<String, String> pathVariables, Map<String, String> extraQueryParams,
                                     Object jsonBody) {
        String url = resolveUrl(endpoint, mode);
        if (url == null || url.isBlank()) {
            throw new IntegrationException("No " + mode + " URL is configured for "
                    + endpoint.getNameEn() + " - set one before switching to this mode.");
        }
        for (Map.Entry<String, String> pv : pathVariables.entrySet()) {
            url = url.replace("{" + pv.getKey() + "}", pv.getValue());
        }

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(url);
        readJsonMap(endpoint.getQueryParamsJson()).forEach(uriBuilder::queryParam);
        if (extraQueryParams != null) extraQueryParams.forEach(uriBuilder::queryParam);

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(endpoint.getTimeoutMs());
        requestFactory.setReadTimeout(endpoint.getTimeoutMs());

        RestClient.Builder builder = RestClient.builder().requestFactory(requestFactory);
        readJsonMap(endpoint.getHeadersJson()).forEach((k, v) -> {
            if (v != null && !v.isBlank()) builder.defaultHeader(k, v);
        });
        applyCredential(builder, endpoint, credentialFor(endpoint));
        RestClient restClient = builder.build();

        HttpMethod method = HttpMethod.valueOf(endpoint.getHttpMethod().toUpperCase());
        var uri = uriBuilder.build().encode().toUri();

        int totalAttempts = Math.max(1, endpoint.getMaxRetries() + 1);
        for (int attempt = 1; attempt <= totalAttempts; attempt++) {
            try {
                RestClient.RequestBodySpec spec = restClient.method(method).uri(uri);
                if (jsonBody != null) {
                    spec.contentType(org.springframework.http.MediaType.APPLICATION_JSON).body(jsonBody);
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> body = spec.retrieve().body(Map.class);
                return body != null ? body : Map.of();
            } catch (RestClientException e) {
                log.warn("[DYNAMIC INTEGRATION] {} attempt {}/{} failed calling {} {}: {}",
                        endpoint.getNameEn(), attempt, totalAttempts, method, uri, e.getMessage());
                if (attempt == totalAttempts) {
                    throw new IntegrationException(ErrorCode.INTEGRATION_UNAVAILABLE, java.util.Map.of("service", endpoint.getNameEn()));
                }
            }
        }
        throw new IntegrationException(ErrorCode.INTEGRATION_UNAVAILABLE, java.util.Map.of("service", endpoint.getNameEn()));
    }

    private String resolveUrl(IntegrationEndpoint endpoint, String mode) {
        return switch (mode.toUpperCase()) {
            case "LIVE" -> endpoint.getUrlLive();
            case "SANDBOX" -> endpoint.getUrlSandbox();
            default -> endpoint.getUrlMock();
        };
    }

    private Map<String, String> readJsonMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            log.warn("Could not parse header/query-param JSON \"{}\": {}", json, e.getMessage());
            return Map.of();
        }
    }
}
