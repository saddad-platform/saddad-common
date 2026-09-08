package com.sadad.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sadad.common.core.api.ApiError;
import com.sadad.common.core.context.RequestContext;
import com.sadad.common.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Every other error path in this API returns the standard {@link ApiError} envelope via
 * {@link GlobalExceptionHandler} - but a request rejected at the security-filter-chain
 * level (no/invalid/superseded token) never reaches a controller, so Spring Security's
 * bare default response would otherwise be the one inconsistent, bodyless exception to
 * that rule. This restores the envelope for that case, and distinguishes a genuinely
 * missing/expired token ({@code UNAUTHENTICATED}) from one whose session was superseded
 * by a newer sign-in elsewhere ({@code SESSION_SUPERSEDED}), so the frontend can show the
 * right message instead of a generic "please sign in".
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        String requestId = RequestContext.currentRequestId();
        boolean sessionSuperseded = Boolean.TRUE.equals(request.getAttribute(JwtAuthenticationFilter.SESSION_SUPERSEDED_ATTRIBUTE));

        ApiError error = sessionSuperseded
                ? ApiError.of("SESSION_SUPERSEDED", "Your session is no longer active - you may have signed in elsewhere or signed out. Please sign in again.", requestId)
                : ApiError.of("UNAUTHENTICATED", "Authentication is required to access this resource.", requestId);

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(error));
    }
}
