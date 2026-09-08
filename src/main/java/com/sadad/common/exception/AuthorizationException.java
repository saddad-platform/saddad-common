package com.sadad.common.exception;

public class AuthorizationException extends PlatformException {
    public AuthorizationException(String message) {
        super("FORBIDDEN_ACCESS", message, 403);
    }
}
