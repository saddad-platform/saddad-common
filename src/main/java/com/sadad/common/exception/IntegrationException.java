package com.sadad.common.exception;

public class IntegrationException extends PlatformException {
    public IntegrationException(String code, String message) {
        super(code, message, 502);
    }

    public IntegrationException(String message) {
        super("UPSTREAM_INTEGRATION_ERROR", message, 502);
    }
}
