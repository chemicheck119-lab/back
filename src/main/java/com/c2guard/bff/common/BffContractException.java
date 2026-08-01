package com.c2guard.bff.common;

public class BffContractException extends RuntimeException {

    private final int status;
    private final String code;
    private final boolean retryable;

    public BffContractException(int status, String code, String message, boolean retryable) {
        super(message);
        this.status = status;
        this.code = code;
        this.retryable = retryable;
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
