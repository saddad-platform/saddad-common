package com.sadad.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sadad.common.errors.ErrorCode;
import com.sadad.common.errors.ErrorMessageResolver;
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
    private final ErrorMessageResolver messages;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper, ErrorMessageResolver messages) {
        this.objectMapper = objectMapper;
        this.messages = messages;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        String requestId = RequestContext.currentRequestId();
        boolean sessionSuperseded = Boolean.TRUE.equals(request.getAttribute(JwtAuthenticationFilter.SESSION_SUPERSEDED_ATTRIBUTE));

        ApiError error = sessionSuperseded
                ? ApiError.of(ErrorCode.SESSION_SUPERSEDED.code(), say(ErrorCode.SESSION_SUPERSEDED), requestId)
                : ApiError.of(ErrorCode.UNAUTHENTICATED.code(), say(ErrorCode.UNAUTHENTICATED), requestId);

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        // Charset stated explicitly: getWriter() otherwise encodes with the container
        // default, and every Arabic character in a translated message becomes '?'.
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(error));
    }

    private String say(ErrorCode code) {
        return messages.resolve(code.code(), RequestContext.currentLocale(),
                java.util.Map.of(), code.getDefaultMessageEn());
    }
}
