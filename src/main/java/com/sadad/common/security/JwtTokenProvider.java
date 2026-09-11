package com.sadad.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long validityInMilliseconds;

    public JwtTokenProvider(
            @Value("${sadad.security.jwt.secret:SADAD_SUPER_SECURE_ENTERPRISE_SIGNING_KEY_32_CHARS_MINIMUM_2026}") String secret,
            @Value("${sadad.security.jwt.expiration-ms:86400000}") long validityInMilliseconds) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.validityInMilliseconds = validityInMilliseconds;
    }

    public String createToken(String userId, String tenantId, String crNumber, List<String> roles) {
        return createToken(userId, tenantId, crNumber, roles, null);
    }

    /**
     * @param sessionId when non-null, embedded as the {@code sid} claim so {@link
     *                  JwtAuthenticationFilter} can reject this token once a newer
     *                  sign-in rotates the user's active session (see {@link SessionRegistry}).
     */
    public String createToken(String userId, String tenantId, String crNumber, List<String> roles, String sessionId) {
        return createToken(userId, tenantId, crNumber, roles, sessionId, null);
    }

    /**
     * @param permissions when non-null, embedded as the {@code perms} claim so that services
     *                    <em>other than the issuer</em> can enforce the same authorisation
     *                    the issuer does.
     *
     *                    <p>The issuer itself should keep resolving permissions from its own
     *                    store on each request - that is what makes a revocation take effect
     *                    immediately. This claim exists for the services that have no access
     *                    to that store and would otherwise enforce nothing at all: they
     *                    accept up to one token lifetime of staleness, which is a far better
     *                    trade than an endpoint that trusts any authenticated caller while
     *                    the interface in front of it implies otherwise.
     */
    public String createToken(String userId, String tenantId, String crNumber, List<String> roles,
                              String sessionId, List<String> permissions) {
        Date now = new Date();
        Date validity = new Date(now.getTime() + validityInMilliseconds);

        var builder = Jwts.builder()
                .subject(userId)
                .claim("tenantId", tenantId)
                .claim("crNumber", crNumber)
                .claim("roles", roles);
        if (sessionId != null) {
            builder.claim("sid", sessionId);
        }
        if (permissions != null && !permissions.isEmpty()) {
            builder.claim("perms", permissions);
        }
        return builder
                .issuedAt(now)
                .expiration(validity)
                .signWith(secretKey)
                .compact();
    }

    public Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String getUsername(String token) {
        return getClaims(token).getSubject();
    }

    public String getTenantId(String token) {
        return getClaims(token).get("tenantId", String.class);
    }

    public String getCrNumber(String token) {
        return getClaims(token).get("crNumber", String.class);
    }

    public String getSessionId(String token) {
        return getClaims(token).get("sid", String.class);
    }

    public boolean validateToken(String token) {
        try {
            Claims claims = getClaims(token);
            return !claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return false;
        }
    }
}
