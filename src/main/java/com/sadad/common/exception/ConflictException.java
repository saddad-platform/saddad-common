package com.sadad.common.exception;

public class ConflictException extends PlatformException {
    public ConflictException(String message) {
        super("RESOURCE_CONFLICT", message, 409);
    }

    public ConflictException(String code, String message) {
        super(code, message, 409);
    }
}
