package com.sadad.common.exception;

import com.sadad.common.errors.ErrorCode;

public class BusinessException extends PlatformException {
    public BusinessException(String code, String message) {
        super(code, message, 422);
    }

    public BusinessException(String message) {
        super("BUSINESS_RULE_VIOLATION", message, 422);
    }

    /** Catalogue-driven: the code decides the wording, the status and the fallback text. */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode, java.util.Map.of());
    }

    /** Catalogue-driven, with values for the entry's {named} placeholders. */
    public BusinessException(ErrorCode errorCode, java.util.Map<String, Object> params) {
        super(errorCode, params);
    }
}
