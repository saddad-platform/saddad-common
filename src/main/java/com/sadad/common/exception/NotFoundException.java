package com.sadad.common.exception;

public class NotFoundException extends PlatformException {
    public NotFoundException(String message) {
        super("RESOURCE_NOT_FOUND", message, 404);
    }

    public NotFoundException(String code, String message) {
        super(code, message, 404);
    }
}
