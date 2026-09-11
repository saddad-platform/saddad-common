package com.sadad.common.exception;

import com.sadad.common.errors.ErrorCode;
import lombok.Getter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The base of every error this platform reports deliberately.
 *
 * <p><b>The code is the message.</b> What a caller receives is decided at the edge, not
 * here: {@code GlobalExceptionHandler} looks the code up in the error catalogue and renders
 * it in the reader's own language. The {@code message} carried here is the developer's
 * version - it goes to the logs, and it is the fallback if a code has no catalogue entry.
 * That fallback is why the message is still required: a new code that nobody has seeded yet
 * degrades to readable English rather than to a blank bubble.
 *
 * <p><b>Values travel as named parameters, not baked into the string.</b> Building
 * {@code "Bill not found: " + reference} produces a sentence that cannot be translated -
 * the English word order is welded to it. Passing {@code reference} as a parameter lets the
 * Arabic entry put the same value wherever Arabic puts it. See
 * {@code MessageInterpolator}.
 */
@Getter
public abstract class PlatformException extends RuntimeException {

    private final String code;
    private final int httpStatus;
    private final List<?> details;
    /** Values for the {@code {named}} placeholders in the catalogue entry for this code. */
    private final Map<String, Object> params;

    protected PlatformException(String code, String message, int httpStatus) {
        this(code, message, httpStatus, Collections.emptyList(), Map.of());
    }

    protected PlatformException(String code, String message, int httpStatus, List<?> details) {
        this(code, message, httpStatus, details, Map.of());
    }

    protected PlatformException(String code, String message, int httpStatus,
                                List<?> details, Map<String, Object> params) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
        this.details = details != null ? details : Collections.emptyList();
        this.params = params != null ? Map.copyOf(params) : Map.of();
    }

    /**
     * Builds a catalogue-driven exception: the code decides both the HTTP status and the
     * wording, and the developer-facing fallback is the code's own English text.
     */
    protected PlatformException(ErrorCode errorCode, Map<String, Object> params) {
        this(errorCode.code(),
             com.sadad.common.errors.MessageInterpolator.interpolate(errorCode.getDefaultMessageEn(), params),
             errorCode.getHttpStatus(), Collections.emptyList(), params);
    }

    /** Convenience for the common shape: {@code of(CODE, "reference", ref, "amount", amt)}. */
    public static Map<String, Object> params(Object... keyThenValue) {
        if (keyThenValue.length % 2 != 0) {
            throw new IllegalArgumentException("Parameters must be supplied in name/value pairs");
        }
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyThenValue.length; i += 2) {
            map.put(String.valueOf(keyThenValue[i]), keyThenValue[i + 1]);
        }
        return map;
    }
}
