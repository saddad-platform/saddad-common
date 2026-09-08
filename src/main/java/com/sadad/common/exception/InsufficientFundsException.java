package com.sadad.common.exception;

public class InsufficientFundsException extends BusinessException {
    public InsufficientFundsException(String message) {
        super("INSUFFICIENT_WALLET_BALANCE", message);
    }
}
