package com.c2guard.bff.confirmation;

import java.time.Instant;

public record ConfirmationResponse(
        String schemaVersion,
        String requestId,
        String incidentId,
        String confirmationId,
        ConfirmationRole role,
        String casNumber,
        Instant createdAt,
        boolean reanalyzeRequired
) {
    public static final String SCHEMA_VERSION = "chemicheck119-dashboard-bff-v1";

    public ConfirmationResponse(String requestId, SubstanceConfirmation confirmation) {
        this(SCHEMA_VERSION, requestId, confirmation.incidentId(),
                confirmation.confirmationId(), confirmation.role(), confirmation.casNumber(),
                confirmation.createdAt(), true);
    }
}
