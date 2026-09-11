package com.sadad.common.errors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Turns an error code into the sentence a particular reader should see.
 *
 * <p>Three sources, in order:
 * <ol>
 *   <li>the catalogue an administrator edits, held in memory - see {@link ErrorCatalogCache};</li>
 *   <li>the wording compiled into {@link ErrorCode}, when the catalogue has no row for the
 *       code, has not loaded yet, or cannot be reached at all;</li>
 *   <li>the message the exception itself carried, for a code this build does not define -
 *       an older service, or a row someone added by hand.</li>
 * </ol>
 *
 * <p>There is deliberately no fourth outcome. This runs while responding to a failure, so
 * "could not resolve the message" is not an option it is allowed to produce; the whole chain
 * exists so that something true and readable always comes out.
 */
@Component
@RequiredArgsConstructor
public class ErrorMessageResolver {

    private final ErrorCatalogCache cache;

    /**
     * @param code     the error code being reported
     * @param locale   "ar" or "en"; anything else is treated as English
     * @param params   values for the entry's {@code {named}} placeholders
     * @param fallback the message the exception carried, used only for an unknown code
     */
    public String resolve(String code, String locale, Map<String, Object> params, String fallback) {
        // A code whose wording is about our deployment rather than about the caller gets
        // the generic sentence. The specific one is already in the log line the handler
        // wrote, tied to the same requestId the caller is shown.
        if (ErrorCode.find(code).filter(known -> !known.isUserSafe()).isPresent()) {
            return resolveSafe(locale);
        }

        String template = template(code, locale);
        if (template == null) {
            // An unknown code. The exception's own message is the best available answer,
            // and it is at least true even though it will not be translated.
            return fallback;
        }
        return MessageInterpolator.interpolate(template, params);
    }

    /** The status the catalogue gives a code, or null to keep the exception's own. */
    public Integer httpStatusFor(String code) {
        ErrorCatalogCache.Translation override = cache.find(code);
        if (override != null && override.httpStatus() != null) return override.httpStatus();
        return ErrorCode.find(code).map(ErrorCode::getHttpStatus).orElse(null);
    }

    /** The sentence shown in place of one that would expose internal detail. */
    private String resolveSafe(String locale) {
        String generic = template(ErrorCode.INTERNAL_SERVER_ERROR.code(), locale);
        return generic != null ? generic
                : (isArabic(locale) ? ErrorCode.INTERNAL_SERVER_ERROR.getDefaultMessageAr()
                                    : ErrorCode.INTERNAL_SERVER_ERROR.getDefaultMessageEn());
    }

    private String template(String code, String locale) {
        ErrorCatalogCache.Translation override = cache.find(code);
        if (override != null) {
            String edited = override.forLocale(locale);
            if (edited != null && !edited.isBlank()) return edited;
        }
        return ErrorCode.find(code)
                .map(known -> isArabic(locale) ? known.getDefaultMessageAr() : known.getDefaultMessageEn())
                .orElse(null);
    }

    private boolean isArabic(String locale) {
        return locale != null && locale.startsWith("ar");
    }
}
