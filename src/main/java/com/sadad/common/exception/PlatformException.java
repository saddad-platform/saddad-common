package com.sadad.common.exception;

import lombok.Getter;

import java.util.Collections;
import java.util.List;

@Getter
public abstract class PlatformException extends RuntimeException {
    private final String code;
    private final int httpStatus;
    private final List<?> details;

    protected PlatformException(String code, String message, int httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
        this.details = Collections.emptyList();
    }

    protected PlatformException(String code, String message, int httpStatus, List<?> details) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
        this.details = details != null ? details : Collections.emptyList();
    }
}
