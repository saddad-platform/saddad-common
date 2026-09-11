package com.sadad.common.exception;

import com.sadad.common.errors.ErrorCode;

public class NotFoundException extends PlatformException {
    public NotFoundException(String message) {
        super("RESOURCE_NOT_FOUND", message, 404);
    }

    public NotFoundException(String code, String message) {
        super(code, message, 404);
    }

    /** Catalogue-driven: the code decides the wording, the status and the fallback text. */
    public NotFoundException(ErrorCode errorCode) {
        super(errorCode, java.util.Map.of());
    }

    /** Catalogue-driven, with values for the entry's {named} placeholders. */
    public NotFoundException(ErrorCode errorCode, java.util.Map<String, Object> params) {
        super(errorCode, params);
    }
}
