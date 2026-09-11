package com.sadad.common.exception;

import com.sadad.common.errors.ErrorCode;

public class IntegrationException extends PlatformException {
    public IntegrationException(String code, String message) {
        super(code, message, 502);
    }

    public IntegrationException(String message) {
        super("UPSTREAM_INTEGRATION_ERROR", message, 502);
    }

    /** Catalogue-driven: the code decides the wording, the status and the fallback text. */
    public IntegrationException(ErrorCode errorCode) {
        super(errorCode, java.util.Map.of());
    }

    /** Catalogue-driven, with values for the entry's {named} placeholders. */
    public IntegrationException(ErrorCode errorCode, java.util.Map<String, Object> params) {
        super(errorCode, params);
    }
}
