package com.sadad.common.security.introspection;

import com.sadad.common.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Local signature + expiry validation only, via the already-existing {@link
 * JwtTokenProvider} - zero network call. Deliberately does NOT consult a {@code
 * SessionRegistry}: this generalizes the exact "validate a token issued elsewhere,
 * shared-secret, skip session-registry" pattern already hand-rolled twice in this
 * codebase (saddad-admin's and saddad-onboarding's own {@code PlatformJwtValidator}) into
 * one written-once implementation. A caller that also needs session-freshness on top of
 * this (today, only saddad-auth's own {@code JwtAuthenticationFilter}) layers that check
 * itself when a local {@code SessionRegistry} bean is present - see that class.
 */
@Component
public class OfflineTokenIntrospector implements TokenIntrospector {

    private final JwtTokenProvider tokenProvider;

    public OfflineTokenIntrospector(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    public IntrospectionResult introspect(String token) {
        try {
            Claims claims = tokenProvider.getClaims(token);
            if (claims.getExpiration() != null && claims.getExpiration().before(new java.util.Date())) {
                return IntrospectionResult.inactive(IntrospectionSource.OFFLINE);
            }
            @SuppressWarnings("unchecked")
            List<String> roles = claims.get("roles", List.class);
            return new IntrospectionResult(
                    true,
                    claims.getSubject(),
                    claims.get("tenantId", String.class),
                    claims.get("crNumber", String.class),
                    roles,
                    claims.get("sid", String.class),
                    claims.getExpiration() != null ? claims.getExpiration().toInstant() : null,
                    IntrospectionSource.OFFLINE
            );
        } catch (JwtException | IllegalArgumentException e) {
            return IntrospectionResult.inactive(IntrospectionSource.OFFLINE);
        }
    }

    @Override
    public IntrospectionSource source() {
        return IntrospectionSource.OFFLINE;
    }
}
