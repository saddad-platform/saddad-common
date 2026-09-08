package com.sadad.common.security.introspection;

import java.time.Instant;
import java.util.List;

/**
 * Uniform result of validating a bearer token, regardless of which {@link TokenIntrospector}
 * produced it - modeled after RFC 7662's introspection response shape ({@code active}/
 * {@code sub}/{@code exp}/roles-as-scope), since {@link OnlineTokenIntrospector} calls a
 * real endpoint shaped this way and {@link OfflineTokenIntrospector} should look identical
 * to its callers.
 *
 * When {@code active} is false, every other field is meaningless (matches RFC 7662: a
 * server "MUST NOT return additional information" beyond active=false for an invalid,
 * expired, or revoked token) - callers must check {@code active} before reading anything
 * else.
 */
public record IntrospectionResult(
        boolean active,
        String subject,
        String tenantId,
        String crNumber,
        List<String> roles,
        String sessionId,
        Instant expiresAt,
        IntrospectionSource source
) {

    public static IntrospectionResult inactive(IntrospectionSource source) {
        return new IntrospectionResult(false, null, null, null, null, null, null, source);
    }
}
