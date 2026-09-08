package com.sadad.common.security;

import com.sadad.common.core.context.RequestContext;
import com.sadad.common.security.introspection.IntrospectionResult;
import com.sadad.common.security.introspection.IntrospectionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Resolves a bearer token via {@link IntrospectionService} (OFFLINE mode - local
 * signature+expiry check only, zero network call, zero behavior change from before this
 * class was refactored onto the shared introspection library) and, when a local {@link
 * SessionRegistry} bean is present, additionally layers a session-currency check on top -
 * this second check is what lets a new sign-in invalidate a still-validly-signed,
 * unexpired token elsewhere. Services with no {@code SessionRegistry} bean (every service
 * except saddad-auth today, the sole owner of that bean) automatically skip that second
 * check, which is exactly the
 * "validate a token issued elsewhere, shared-secret, skip session-registry" behavior
 * previously hand-rolled per-service (saddad-admin's/saddad-onboarding's own {@code
 * PlatformJwtValidator}/{@code PlatformAdminAuthFilter}) - wiring this shared filter
 * directly is the generalized replacement for those copies.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** Set on the request when a token is well-formed but its session was superseded by a newer sign-in, so {@code RestAuthenticationEntryPoint} can report the specific reason instead of a generic 401. */
    public static final String SESSION_SUPERSEDED_ATTRIBUTE = "sadad.sessionSuperseded";

    private final IntrospectionService introspectionService;
    private final Optional<SessionRegistry> sessionRegistry;

    public JwtAuthenticationFilter(IntrospectionService introspectionService, Optional<SessionRegistry> sessionRegistry) {
        this.introspectionService = introspectionService;
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String token = resolveToken(request);
        if (StringUtils.hasText(token)) {
            IntrospectionResult result = introspectionService.introspect(token);
            if (result.active()) {
                String userId = result.subject();
                String tenantId = result.tenantId();
                String sessionId = result.sessionId();

                boolean sessionStillCurrent = sessionId == null || sessionRegistry.isEmpty()
                        || sessionRegistry.get().isCurrent(userId, sessionId);

                if (!sessionStillCurrent) {
                    request.setAttribute(SESSION_SUPERSEDED_ATTRIBUTE, Boolean.TRUE);
                } else {
                    AuthenticatedUser user = AuthenticatedUser.builder()
                            .userId(userId)
                            .tenantId(tenantId)
                            .crNumber(result.crNumber())
                            .roles(result.roles())
                            .build();

                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(auth);

                    // Synchronize authenticated identity into RequestContext
                    RequestContext ctx = RequestContext.get();
                    if (ctx != null) {
                        ctx.setUserId(userId);
                        ctx.setTenantId(tenantId);
                        if (result.roles() != null && !result.roles().isEmpty()) {
                            ctx.setUserRole(result.roles().get(0));
                        }
                    }
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}
