package com.sadad.common.exception;

public class BusinessException extends PlatformException {
    public BusinessException(String code, String message) {
        super(code, message, 422);
    }

    public BusinessException(String message) {
        super("BUSINESS_RULE_VIOLATION", message, 422);
    }
}
