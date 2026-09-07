package com.c2guard.bff.confirmation;

record ConfirmationCancelCommand(
        String incidentId,
        ConfirmationRole role,
        String confirmationId,
        String userId,
        String organizationId,
        String requestId
) {
}
