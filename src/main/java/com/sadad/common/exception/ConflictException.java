package com.sadad.common.exception;

import com.sadad.common.errors.ErrorCode;

public class ConflictException extends PlatformException {
    public ConflictException(String message) {
        super("RESOURCE_CONFLICT", message, 409);
    }

    public ConflictException(String code, String message) {
        super(code, message, 409);
    }

    /** Catalogue-driven: the code decides the wording, the status and the fallback text. */
    public ConflictException(ErrorCode errorCode) {
        super(errorCode, java.util.Map.of());
    }

    /** Catalogue-driven, with values for the entry's {named} placeholders. */
    public ConflictException(ErrorCode errorCode, java.util.Map<String, Object> params) {
        super(errorCode, params);
    }
}
