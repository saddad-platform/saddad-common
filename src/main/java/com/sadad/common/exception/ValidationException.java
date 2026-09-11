package com.sadad.common.exception;

import com.sadad.common.errors.ErrorCode;

import java.util.List;

public class ValidationException extends PlatformException {
    public ValidationException(String message, List<?> details) {
        super("VALIDATION_ERROR", message, 400, details);
    }

    public ValidationException(String message) {
        super("VALIDATION_ERROR", message, 400);
    }

    /** Catalogue-driven: the code decides the wording, the status and the fallback text. */
    public ValidationException(ErrorCode errorCode) {
        super(errorCode, java.util.Map.of());
    }

    /** Catalogue-driven, with values for the entry's {named} placeholders. */
    public ValidationException(ErrorCode errorCode, java.util.Map<String, Object> params) {
        super(errorCode, params);
    }

    public ValidationException(String code, String message) {
        super(code, message, 400);
    }
}
