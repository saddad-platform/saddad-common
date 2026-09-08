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
