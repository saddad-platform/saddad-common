package com.sadad.common.core.util;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * Renders an admin-configured template string (an email/SMS body, subject, ...) against a
 * parameter map using FreeMarker (<code>${token}</code>, <code>&lt;#if&gt;</code>,
 * <code>&lt;#list&gt;</code>, ...) - the one templating engine every admin-configurable
 * message body in this platform uses, in saddad-onboarding, saddad-admin (email templates),
 * and saddad-auth (sign-in/OTP SMS templates) alike. Only this
 * stateless rendering mechanic is shared (the same way IbanUtil/MaskingUtil are) - each
 * service keeps its own EmailService/SMS-sending code entirely separate, per this
 * codebase's "separate deployables, no shared beans" convention.
 */
public final class TemplateRenderingUtil {

    private static final Configuration FREEMARKER = new Configuration(Configuration.VERSION_2_3_32);
    static {
        FREEMARKER.setNumberFormat("computer");
        FREEMARKER.setLogTemplateExceptions(false);
        FREEMARKER.setFallbackOnNullLoopVariable(false);
    }

    private TemplateRenderingUtil() {}

    /**
     * @param templateName a short label identifying the template for error messages only
     *                     (e.g. "ACCOUNT_ACTIVATED" or "OTP_SMS") - this never reads from a
     *                     template loader/directory; the source is always
     *                     {@code templateSource} itself.
     * @param model        token values referenced by the template as {@code ${key}}. A null
     *                     value is treated as an empty string rather than a FreeMarker
     *                     "missing value" error, so an admin template can reference an
     *                     optional token without every call site needing to guard against
     *                     nulls itself.
     * @throws TemplateRenderException if the template is malformed, or references a token
     *                                 not present in {@code model} at all.
     */
    public static String render(String templateName, String templateSource, Map<String, ?> model) {
        Map<String, Object> safeModel = new HashMap<>();
        model.forEach((key, value) -> safeModel.put(key, value != null ? value : ""));

        try {
            Template template = new Template(templateName, templateSource, FREEMARKER);
            StringWriter writer = new StringWriter();
            template.process(safeModel, writer);
            return writer.toString();
        } catch (IOException | TemplateException e) {
            throw new TemplateRenderException("Failed to render template '" + templateName + "': " + e.getMessage(), e);
        }
    }

    public static class TemplateRenderException extends RuntimeException {
        public TemplateRenderException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
