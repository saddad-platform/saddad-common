package com.sadad.common.security;

import com.sadad.common.core.context.RequestContext;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Resolves a bearer token against this service's own signing key - a local signature and
 * expiry check, no network call - and, when a local {@link SessionRegistry} bean is present,
 * additionally layers a session-currency check on top. That second check is what lets a new
 * sign-in invalidate a still-validly-signed, unexpired token elsewhere. Services with no
 * {@code SessionRegistry} bean skip it automatically, which is exactly the
 * "validate a token issued elsewhere, shared secret, no session registry" behavior every
 * downstream domain service wants.
 *
 * <p>This used to delegate to an {@code IntrospectionService} abstraction that could
 * dispatch to an OFFLINE local check or an ONLINE call back to the issuing service's own
 * {@code /v1/internal/auth/introspect}. Nothing ever selected ONLINE - no configuration
 * anywhere set {@code sadad.introspection.online.base-url}, so that bean never existed, and
 * the only caller in the platform used the OFFLINE default - so the whole eight-class
 * indirection resolved to "parse the token locally" on every single request. It was removed
 * along with the endpoint it would have called. If revocation-sensitive online introspection
 * is ever genuinely needed, add it then, against a real requirement.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** Set on the request when a token is well-formed but its session was superseded by a newer sign-in, so {@code RestAuthenticationEntryPoint} can report the specific reason instead of a generic 401. */
    public static final String SESSION_SUPERSEDED_ATTRIBUTE = "sadad.sessionSuperseded";

    private final JwtTokenProvider tokenProvider;
    private final Optional<SessionRegistry> sessionRegistry;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, Optional<SessionRegistry> sessionRegistry) {
        this.tokenProvider = tokenProvider;
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String token = resolveToken(request);
        if (StringUtils.hasText(token)) {
            Claims claims = parse(token);
            if (claims != null) {
                String userId = claims.getSubject();
                String tenantId = claims.get("tenantId", String.class);
                String sessionId = claims.get("sid", String.class);
                List<String> roles = roles(claims);

                boolean sessionStillCurrent = sessionId == null || sessionRegistry.isEmpty()
                        || sessionRegistry.get().isCurrent(userId, sessionId);

                if (!sessionStillCurrent) {
                    request.setAttribute(SESSION_SUPERSEDED_ATTRIBUTE, Boolean.TRUE);
                } else {
                    AuthenticatedUser user = AuthenticatedUser.builder()
                            .userId(userId)
                            .tenantId(tenantId)
                            .crNumber(claims.get("crNumber", String.class))
                            .roles(roles)
                            .build();

                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(auth);

                    // Synchronize the authenticated identity into RequestContext. This is
                    // the ONLY place tenant scope is ever populated - SadadRequestContextFilter
                    // deliberately no longer seeds it from a client-supplied header (see that
                    // class's own javadoc), so every downstream
                    // RequestContext.currentTenantId() read is server-derived by construction.
                    RequestContext ctx = RequestContext.get();
                    if (ctx != null) {
                        ctx.setUserId(userId);
                        ctx.setTenantId(tenantId);
                        if (roles != null && !roles.isEmpty()) {
                            ctx.setUserRole(roles.get(0));
                        }
                    }
                    if (tenantId != null && !tenantId.isBlank()) {
                        MDC.put("tenantId", tenantId);
                    }
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    /** Returns the verified claims, or null for any token this service cannot vouch for -
     * wrong signature, malformed, or expired. Never throws: an unauthenticated request is a
     * normal outcome here, and the security chain's entry point turns it into a 401. */
    private Claims parse(String token) {
        try {
            return tokenProvider.validateToken(token) ? tokenProvider.getClaims(token) : null;
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> roles(Claims claims) {
        Object raw = claims.get("roles");
        return raw instanceof List ? (List<String>) raw : List.of();
    }

    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}
