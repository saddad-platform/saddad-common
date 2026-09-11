package com.sadad.common.core.context;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RequestContext {
    private static final ThreadLocal<RequestContext> CURRENT = new ThreadLocal<>();

    private String correlationId;
    private String requestId;
    private String tenantId;
    private String userId;
    private String userRole;
    private String locale; // "ar" or "en"
    private String channel; // "WEB", "MOBILE", "INTEGRATION"
    private String clientIp;

    public static RequestContext get() {
        return CURRENT.get();
    }

    public static void set(RequestContext context) {
        CURRENT.set(context);
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static String currentTenantId() {
        RequestContext ctx = get();
        return ctx != null ? ctx.getTenantId() : null;
    }

    public static String currentUserId() {
        RequestContext ctx = get();
        return ctx != null ? ctx.getUserId() : null;
    }

    /**
     * The language this request asked for, "ar" or "en". Defaults to Arabic, which is the
     * platform's primary language and what a caller that says nothing should get - the
     * portals both default to Arabic too.
     */
    public static String currentLocale() {
        RequestContext context = get();
        return context == null || context.locale == null ? "ar" : context.locale;
    }

    public static String currentRequestId() {
        RequestContext ctx = get();
        return ctx != null ? ctx.getRequestId() : null;
    }
}
