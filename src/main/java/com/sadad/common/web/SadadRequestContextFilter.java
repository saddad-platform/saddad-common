package com.sadad.common.web;

import com.sadad.common.core.context.RequestContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Seeds the per-request {@link RequestContext} with the transport-level facts a request
 * carries on its own: correlation/request ids, channel, locale and client IP.
 *
 * <p><strong>Tenant is deliberately absent here.</strong> This filter used to read an
 * {@code X-Tenant-Id} request header into the context, which meant an unauthenticated
 * request could pre-seed a tenant scope of its own choosing before any authentication
 * filter ran. Tenant scope is now populated in exactly one place -
 * {@link com.sadad.common.security.JwtAuthenticationFilter}, from the verified JWT claim -
 * matching saddad-docs/02-SOLUTION-ARCHITECTURE.md Section 7: "Never trust a tenant
 * identifier supplied by the client. Resolve tenant context from authenticated claims."
 */
@Component("sadadRequestContextFilter")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SadadRequestContextFilter extends OncePerRequestFilter {

    public static final String HEADER_CORRELATION_ID = "X-Correlation-Id";
    public static final String HEADER_REQUEST_ID = "X-Request-Id";
    public static final String HEADER_CHANNEL = "X-Channel";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String correlationId = request.getHeader(HEADER_CORRELATION_ID);
        if (!StringUtils.hasText(correlationId)) {
            correlationId = UUID.randomUUID().toString();
        }

        String requestId = request.getHeader(HEADER_REQUEST_ID);
        if (!StringUtils.hasText(requestId)) {
            requestId = UUID.randomUUID().toString();
        }

        String channel = request.getHeader(HEADER_CHANNEL);
        String locale = request.getHeader("Accept-Language");
        if (locale != null && locale.contains("ar")) {
            locale = "ar";
        } else {
            locale = "en";
        }

        RequestContext context = RequestContext.builder()
                .correlationId(correlationId)
                .requestId(requestId)
                .channel(StringUtils.hasText(channel) ? channel : "WEB")
                .locale(locale)
                .clientIp(request.getRemoteAddr())
                .build();

        RequestContext.set(context);

        MDC.put("correlationId", correlationId);
        MDC.put("requestId", requestId);

        response.setHeader(HEADER_CORRELATION_ID, correlationId);
        response.setHeader(HEADER_REQUEST_ID, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            RequestContext.clear();
            MDC.clear();
        }
    }
}