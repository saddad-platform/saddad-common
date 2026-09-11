package com.sadad.common.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sadad.common.secrets.IntegrationSecretCrypto;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * That a credential stored in the console actually reaches the vendor.
 *
 * <p>Against a real socket rather than a mocked RestClient, because the thing worth proving
 * is that the header leaves the process. Every part of the chain in between - the registry
 * lookup, the decryption, the header name configured on the endpoint - is a place the
 * credential could be silently dropped, and a mock of the HTTP client would assert only that
 * the code did what the code does.
 */
class DynamicHttpCallerCredentialTest {

    /** A throwaway 32-byte key; this test encrypts and decrypts entirely within itself. */
    private static final String TEST_KEY = "CXBWqcDQf5wuw/wBgviYfsAlhyLgibX4YadxcoGlsf8=";

    private HttpServer server;
    private final Map<String, String> lastRequestHeaders = new ConcurrentHashMap<>();
    private final IntegrationSecretCrypto crypto = new IntegrationSecretCrypto(TEST_KEY);
    private IntegrationProviderConfigRepository providerRepository;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/thing", exchange -> {
            lastRequestHeaders.clear();
            exchange.getRequestHeaders().forEach((k, v) -> lastRequestHeaders.put(k.toLowerCase(), String.join(",", v)));
            try (InputStream ignored = exchange.getRequestBody()) { ignored.readAllBytes(); }
            byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) { out.write(body); }
        });
        server.start();
        providerRepository = mock(IntegrationProviderConfigRepository.class);
    }

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    private String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/thing";
    }

    @SuppressWarnings("unchecked")
    private DynamicHttpCaller caller(boolean cryptoAvailable) {
        ObjectProvider<IntegrationSecretCrypto> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(cryptoAvailable ? crypto : null);
        return new DynamicHttpCaller(new ObjectMapper(), providerRepository, provider);
    }

    private IntegrationEndpoint endpoint(UUID providerId, String secretHeaderName) {
        return IntegrationEndpoint.builder()
                .providerId(providerId)
                .endpointKey("THING")
                .nameEn("Thing")
                .nameAr("شيء")
                .httpMethod("GET")
                .urlLive(url())
                .urlSandbox(url())
                .urlMock(url())
                .timeoutMs(5_000)
                .maxRetries(0)
                .secretHeaderName(secretHeaderName)
                .build();
    }

    private void providerHolds(UUID id, String plaintextCredential) {
        IntegrationProviderConfig config = new IntegrationProviderConfig();
        config.setId(id);
        config.setProviderId("test-provider");
        config.setMode("LIVE");
        config.setCredentialEncrypted(
                plaintextCredential == null ? null : crypto.encrypt(plaintextCredential));
        when(providerRepository.findById(id)).thenReturn(Optional.of(config));
    }

    @Test
    void aStoredCredentialIsSentInTheHeaderTheEndpointNames() {
        UUID providerId = UUID.randomUUID();
        providerHolds(providerId, "the-real-vendor-key");

        caller(true).call(endpoint(providerId, "Ocp-Apim-Subscription-Key"), "LIVE", Map.of(), Map.of());

        assertEquals("the-real-vendor-key", lastRequestHeaders.get("ocp-apim-subscription-key"),
                "the credential an operator stored must reach the vendor");
    }

    @Test
    void withNoHeaderNameConfiguredTheCredentialIsNotGuessedIntoOne() {
        // Sending a secret in a header the vendor did not ask for leaks it to them for
        // nothing. If the endpoint does not say where it goes, it does not go.
        UUID providerId = UUID.randomUUID();
        providerHolds(providerId, "the-real-vendor-key");

        caller(true).call(endpoint(providerId, null), "LIVE", Map.of(), Map.of());

        assertTrue(lastRequestHeaders.values().stream().noneMatch(v -> v.contains("the-real-vendor-key")),
                "no header should carry the credential when none is named");
    }

    @Test
    void aProviderWithNoCredentialCallsWithoutOne() {
        UUID providerId = UUID.randomUUID();
        providerHolds(providerId, null);

        caller(true).call(endpoint(providerId, "Ocp-Apim-Subscription-Key"), "LIVE", Map.of(), Map.of());

        assertNull(lastRequestHeaders.get("ocp-apim-subscription-key"));
    }

    @Test
    void aServiceWithoutTheSharedKeyStillMakesTheCall() {
        // The cipher bean is conditional on the key being configured. A service without it
        // must degrade to an unauthenticated call, not fail to start or throw here.
        UUID providerId = UUID.randomUUID();
        providerHolds(providerId, "the-real-vendor-key");

        caller(false).call(endpoint(providerId, "Ocp-Apim-Subscription-Key"), "LIVE", Map.of(), Map.of());

        assertNull(lastRequestHeaders.get("ocp-apim-subscription-key"));
    }

    @Test
    void aRegistryFailureDoesNotTakeTheCallDownWithIt() {
        // A configuration read that can fail a payment is a worse problem than a stale key.
        UUID providerId = UUID.randomUUID();
        when(providerRepository.findById(any())).thenThrow(new IllegalStateException("database is down"));

        caller(true).call(endpoint(providerId, "Ocp-Apim-Subscription-Key"), "LIVE", Map.of(), Map.of());

        assertNull(lastRequestHeaders.get("ocp-apim-subscription-key"));
    }
}
