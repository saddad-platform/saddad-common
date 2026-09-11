package com.sadad.common.exception;

import com.sadad.common.errors.ErrorCode;

public class AuthorizationException extends PlatformException {
    public AuthorizationException(String message) {
        super("FORBIDDEN_ACCESS", message, 403);
    }

    /** Catalogue-driven: the code decides the wording, the status and the fallback text. */
    public AuthorizationException(ErrorCode errorCode) {
        super(errorCode, java.util.Map.of());
    }

    /** Catalogue-driven, with values for the entry's {named} placeholders. */
    public AuthorizationException(ErrorCode errorCode, java.util.Map<String, Object> params) {
        super(errorCode, params);
    }

    public AuthorizationException(String code, String message) {
        super(code, message, 403);
    }
}
