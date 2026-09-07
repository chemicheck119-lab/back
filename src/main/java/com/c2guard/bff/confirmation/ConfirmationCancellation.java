package com.c2guard.bff.confirmation;

import java.time.Instant;

public record ConfirmationCancellation(
        String confirmationId,
        String incidentId,
        ConfirmationRole role,
        String cancelledByUserId,
        String cancelledByOrganizationId,
        Instant cancelledAt,
        String cancelledRequestId
) {
}
