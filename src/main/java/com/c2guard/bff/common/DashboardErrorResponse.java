package com.c2guard.bff.common;

public record DashboardErrorResponse(
        String schemaVersion,
        String requestId,
        DashboardErrorDetail error,
        boolean resetAllowed
) {
    public static final String SCHEMA_VERSION = "chemicheck119-dashboard-bff-v1";

    public DashboardErrorResponse(String requestId, String code, String message,
                                  boolean retryable) {
        this(SCHEMA_VERSION, requestId, new DashboardErrorDetail(code, message, retryable), false);
    }

    public record DashboardErrorDetail(String code, String message, boolean retryable) {
    }
}
