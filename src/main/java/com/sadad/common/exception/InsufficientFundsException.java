package com.sadad.common.exception;

import com.sadad.common.errors.ErrorCode;

import java.util.Map;

public class InsufficientFundsException extends BusinessException {

    public InsufficientFundsException(String message) {
        super(ErrorCode.WALLET_INSUFFICIENT_FUNDS.code(), message);
    }

    /** The reference being settled, so the catalogue entry can name it in either language. */
    public InsufficientFundsException(Map<String, Object> params) {
        super(ErrorCode.WALLET_INSUFFICIENT_FUNDS, params);
    }
}
