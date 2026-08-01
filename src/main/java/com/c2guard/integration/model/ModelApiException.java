package com.c2guard.integration.model;

import java.util.List;

public class ModelApiException extends RuntimeException {

    private final ModelApiErrorKind kind;
    private final String code;
    private final String upstreamMessage;
    private final boolean retryable;
    private final Integer upstreamStatus;
    private final String requestId;
    private final String occurredAtUtc;
    private final List<String> fields;

    public ModelApiException(ModelApiErrorKind kind, String code, String message,
                             boolean retryable, Integer upstreamStatus,
                             String requestId, List<String> fields, Throwable cause) {
        this(kind, code, message, retryable, upstreamStatus, requestId, null, fields, cause);
    }

    public ModelApiException(ModelApiErrorKind kind, String code, String message,
                             boolean retryable, Integer upstreamStatus,
                             String requestId, String occurredAtUtc,
                             List<String> fields, Throwable cause) {
        super(code + ": " + safeMessage(message), cause);
        this.kind = kind;
        this.code = code;
        this.upstreamMessage = message;
        this.retryable = retryable;
        this.upstreamStatus = upstreamStatus;
        this.requestId = requestId;
        this.occurredAtUtc = occurredAtUtc;
        this.fields = fields == null ? List.of() : List.copyOf(fields);
    }

    public ModelApiErrorKind getKind() {
        return kind;
    }

    public String getCode() {
        return code;
    }

    public String getUpstreamMessage() {
        return upstreamMessage;
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

    public String getOccurredAtUtc() {
        return occurredAtUtc;
    }

    public List<String> getFields() {
        return fields;
    }

    public boolean allowsAutomaticRetry() {
        return (kind == ModelApiErrorKind.NETWORK && "MODEL_CONNECTION_FAILED".equals(code))
                || (upstreamStatus != null && upstreamStatus == 503 && retryable);
    }

    private static String safeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "모델 API 호출에 실패했습니다.";
        }
        String singleLine = message.replace('\n', ' ').replace('\r', ' ').trim();
        return singleLine.length() <= 500 ? singleLine : singleLine.substring(0, 500);
    }
}
