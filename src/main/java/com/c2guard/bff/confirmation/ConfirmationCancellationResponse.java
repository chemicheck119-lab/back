package com.c2guard.bff.confirmation;

import java.time.Instant;

public record ConfirmationCancellationResponse(
        String schemaVersion,
        String requestId,
        String incidentId,
        String confirmationId,
        ConfirmationRole role,
        ConfirmationStatus status,
        Instant cancelledAt,
        boolean reanalyzeRequired
) {
    public static final String SCHEMA_VERSION = "chemicheck119-dashboard-bff-v1";

    public ConfirmationCancellationResponse(String requestId,
                                            ConfirmationCancellation cancellation) {
        this(SCHEMA_VERSION, requestId, cancellation.incidentId(),
                cancellation.confirmationId(), cancellation.role(),
                ConfirmationStatus.CANCELLED, cancellation.cancelledAt(), true);
    }
}
