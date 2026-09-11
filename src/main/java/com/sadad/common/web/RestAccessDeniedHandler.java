package com.sadad.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sadad.common.errors.ErrorCode;
import com.sadad.common.errors.ErrorMessageResolver;
import com.sadad.common.core.api.ApiError;
import com.sadad.common.core.context.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Without this bean, {@code HttpSecurity.exceptionHandling()} falls back to a default
 * {@code AccessDeniedHandlerImpl} for a denial at the filter-chain level (e.g. {@code
 * authorizeHttpRequests().anyRequest().hasRole(...)}) - but in practice, on this stack
 * (Spring Security 6 / Boot 3.3.3), an authenticated-but-insufficient-role request was
 * observed reaching {@link RestAuthenticationEntryPoint} instead (401 "Authentication is
 * required") rather than a 403. Registering this handler explicitly is what makes the 403
 * path used, matching {@link GlobalExceptionHandler}'s {@code ACCESS_DENIED} envelope for
 * the (rarer) case of a denial thrown from inside a controller via {@code @PreAuthorize}.
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;
    private final ErrorMessageResolver messages;

    public RestAccessDeniedHandler(ObjectMapper objectMapper, ErrorMessageResolver messages) {
        this.objectMapper = objectMapper;
        this.messages = messages;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        String requestId = RequestContext.currentRequestId();
        ApiError error = ApiError.of(ErrorCode.ACCESS_DENIED.code(),
                messages.resolve(ErrorCode.ACCESS_DENIED.code(), RequestContext.currentLocale(),
                        java.util.Map.of(), ErrorCode.ACCESS_DENIED.getDefaultMessageEn()),
                requestId);

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        // Charset stated explicitly: getWriter() otherwise encodes with the container
        // default, and every Arabic character in a translated message becomes '?'.
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(error));
    }
}
