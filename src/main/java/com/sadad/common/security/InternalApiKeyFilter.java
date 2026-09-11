package com.sadad.common.security;

import com.sadad.common.core.api.ApiError;
import com.sadad.common.core.context.RequestContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Trust boundary for /v1/internal/** - a shared-secret header check, not a JWT, since these
 * calls come from another deployable, not a human session. /v1/internal/** is permitAll in
 * each service's own SecurityConfig (JWT-based auth doesn't apply to this call at all); this
 * filter is what actually gates it. Originally hand-rolled in saddad-core alone (as a
 * provisioning-specific filter); generalized here once saddad-admin and saddad-notifications
 * needed the identical check, following the same "written once in saddad-common" path as
 * {@link JwtAuthenticationFilter}. There's a single flat shared secret across every
 * caller/callee today (no per-caller identity) - that's a known, accepted simplification, not
 * an oversight.
 */
@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    private static final String HEADER_NAME = "X-Internal-Api-Key";

    private final String expectedApiKey;
    private final ObjectMapper objectMapper;

    public InternalApiKeyFilter(@Value("${sadad.internal.api-key}") String expectedApiKey,
                                 ObjectMapper objectMapper) {
        this.expectedApiKey = expectedApiKey;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (request.getRequestURI().startsWith(request.getContextPath() + "/v1/internal/")) {
            String providedKey = request.getHeader(HEADER_NAME);
            if (!StringUtils.hasText(providedKey) || !providedKey.equals(expectedApiKey)) {
                String requestId = RequestContext.currentRequestId();
                ApiError error = ApiError.of("UNAUTHENTICATED", "A valid internal API key is required for this endpoint", requestId);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                // Deliberately not translated: the only readers of this are other services,
                // and an API key failure is an operator's problem, not a customer's. The
                // charset is still declared, so this stays correct if it ever carries text
                // that is not plain ASCII.
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
                response.getWriter().write(objectMapper.writeValueAsString(error));
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
