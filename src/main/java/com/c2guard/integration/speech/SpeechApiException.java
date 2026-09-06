package com.c2guard.integration.speech;

public class SpeechApiException extends RuntimeException {

    private final SpeechApiErrorKind kind;
    private final String code;
    private final boolean retryable;
    private final Integer upstreamStatus;
    private final String requestId;

    public SpeechApiException(SpeechApiErrorKind kind, String code, String message,
                              boolean retryable, Integer upstreamStatus,
                              String requestId, Throwable cause) {
        super(code + ": " + safeMessage(message), cause);
        this.kind = kind;
        this.code = code;
        this.retryable = retryable;
        this.upstreamStatus = upstreamStatus;
        this.requestId = requestId;
    }

    public SpeechApiErrorKind getKind() {
        return kind;
    }

    public String getCode() {
        return code;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public Integer getUpstreamStatus() {
        return upstreamStatus;
    }

    public String getRequestId() {
        return requestId;
    }

    private static String safeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "Speech API 호출에 실패했습니다.";
        }
        String singleLine = message.replace('\n', ' ').replace('\r', ' ').trim();
        return singleLine.length() <= 300 ? singleLine : singleLine.substring(0, 300);
    }
}
