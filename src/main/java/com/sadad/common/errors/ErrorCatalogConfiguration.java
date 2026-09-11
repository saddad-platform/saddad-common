package com.sadad.common.errors;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Wires the error catalogue into every service that depends on saddad-common.
 *
 * <p>There is nothing to switch on. A service gets working, translated error messages from
 * the moment it starts, out of {@link ErrorCode}; configuring
 * {@code sadad.error-catalog.url} additionally lets an administrator's edits reach it. A
 * service with no configuration - or one that cannot reach saddad-admin - behaves correctly
 * and silently, which is the only acceptable behaviour for machinery that runs on the
 * failure path.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(ErrorCatalogProperties.class)
public class ErrorCatalogConfiguration {

    @Bean
    public ErrorCatalogCache errorCatalogCache(ErrorCatalogProperties properties, ObjectMapper objectMapper) {
        if (properties.getUrl() == null || properties.getUrl().isBlank()) {
            log.info("No error-catalog URL configured - using the built-in messages only");
            return new ErrorCatalogCache(properties.getRefreshInterval(), Map::of);
        }
        RestClient client = RestClient.builder().baseUrl(properties.getUrl()).build();
        return new ErrorCatalogCache(properties.getRefreshInterval(), () -> fetch(client, properties));
    }

    /**
     * Loads once at startup, on a virtual thread so a slow or unreachable saddad-admin
     * cannot hold up this service becoming ready. The first request would trigger a load
     * anyway; doing it here means the first customer to hit an error already has the
     * administrator's wording rather than the built-in text.
     */
    @Bean
    public ErrorCatalogWarmUp errorCatalogWarmUp(ErrorCatalogCache cache) {
        return new ErrorCatalogWarmUp(cache);
    }

    /** Named class rather than a lambda so the listener is easy to find and to test. */
    public static class ErrorCatalogWarmUp {
        private final ErrorCatalogCache cache;

        public ErrorCatalogWarmUp(ErrorCatalogCache cache) {
            this.cache = cache;
        }

        @EventListener(ApplicationReadyEvent.class)
        public void loadOnStartup() {
            Thread.ofVirtual().name("error-catalog-warmup").start(cache::refreshNow);
        }
    }

    private static Map<String, ErrorCatalogCache.Translation> fetch(
            RestClient client, ErrorCatalogProperties properties) {
        List<Map<String, Object>> rows = client.get()
                .uri("/v1/internal/error-catalog")
                .header("X-Internal-Api-Key", properties.getApiKey())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        if (rows == null) return Map.of();

        Map<String, ErrorCatalogCache.Translation> byCode = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object code = row.get("errorCode");
            if (code == null) continue;
            byCode.put(String.valueOf(code), new ErrorCatalogCache.Translation(
                    String.valueOf(code),
                    asText(row.get("messageEn")),
                    asText(row.get("messageAr")),
                    row.get("httpStatus") instanceof Number n ? n.intValue() : null));
        }
        return byCode;
    }

    private static String asText(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
