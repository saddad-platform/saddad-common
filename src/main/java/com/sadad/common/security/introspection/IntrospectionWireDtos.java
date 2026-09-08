package com.sadad.common.security.introspection;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/** Wire shapes for {@link OnlineTokenIntrospector}'s call to a token-issuing service's own
 * {@code POST /v1/internal/auth/introspect} endpoint - JSON + {@code X-Internal-Api-Key},
 * matching this codebase's established internal-endpoint convention (not RFC 7662's literal
 * form-encoded request), but carrying the same information an RFC 7662 response would. */
public class IntrospectionWireDtos {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IntrospectRequest {
        private String token;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IntrospectResponse {
        private boolean active;
        private String subject;
        private String tenantId;
        private String crNumber;
        private List<String> roles;
        private String sessionId;
        private Instant expiresAt;
    }
}
