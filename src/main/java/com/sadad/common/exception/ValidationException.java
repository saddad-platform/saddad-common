package com.sadad.common.exception;

import java.util.List;

public class ValidationException extends PlatformException {
    public ValidationException(String message, List<?> details) {
        super("VALIDATION_ERROR", message, 400, details);
    }

    public ValidationException(String message) {
        super("VALIDATION_ERROR", message, 400);
    }
}
