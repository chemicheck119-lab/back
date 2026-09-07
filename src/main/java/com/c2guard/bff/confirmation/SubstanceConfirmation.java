package com.c2guard.bff.confirmation;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Objects;

public record SubstanceConfirmation(
        String confirmationId,
        String incidentId,
        ConfirmationRole role,
        String casNumber,
        String displayName,
        ConfirmationBasis confirmationBasis,
        OffsetDateTime observedAt,
        String confirmedByUserId,
        String confirmedByOrganizationId,
        Instant createdAt,
        String createdRequestId,
        long revision,
        ConfirmationStatus status,
        String supersededByConfirmationId,
        Instant supersededAt
) {
    public SubstanceConfirmation supersededBy(String nextConfirmationId, Instant at) {
        return new SubstanceConfirmation(confirmationId, incidentId, role, casNumber,
                displayName, confirmationBasis, observedAt, confirmedByUserId,
                confirmedByOrganizationId, createdAt, createdRequestId, revision,
                ConfirmationStatus.SUPERSEDED, nextConfirmationId, at);
    }

    public SubstanceConfirmation cancelledAt(Instant at) {
        return new SubstanceConfirmation(confirmationId, incidentId, role, casNumber,
                displayName, confirmationBasis, observedAt, confirmedByUserId,
                confirmedByOrganizationId, createdAt, createdRequestId, revision,
                ConfirmationStatus.CANCELLED, null, at);
    }

    boolean semanticallyEquals(ConfirmationSaveCommand command) {
        return role == command.role()
                && casNumber.equals(command.casNumber())
                && Objects.equals(displayName, command.displayName())
                && confirmationBasis == command.confirmationBasis()
                && observedAt.toInstant().equals(command.observedAt().toInstant());
    }
}
