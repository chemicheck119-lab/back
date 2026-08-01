package com.c2guard.bff.confirmation;

import java.time.OffsetDateTime;

record ConfirmationSaveCommand(
        String incidentId,
        ConfirmationRole role,
        String casNumber,
        String displayName,
        ConfirmationBasis confirmationBasis,
        OffsetDateTime observedAt,
        String userId,
        String organizationId,
        String requestId
) {
}
