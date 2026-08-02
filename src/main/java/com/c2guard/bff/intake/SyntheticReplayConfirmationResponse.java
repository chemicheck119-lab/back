package com.c2guard.bff.intake;

import com.c2guard.bff.confirmation.ConfirmationRole;

import java.time.Instant;

public record SyntheticReplayConfirmationResponse(
        String schemaVersion,
        String requestId,
        String incidentId,
        String confirmationId,
        ConfirmationRole role,
        String casNumber,
        String displayName,
        IncidentDataClassification dataClassification,
        String confirmationType,
        Instant createdAt,
        int confirmedCount,
        boolean allRequiredConfirmed,
        boolean reanalyzeRequired,
        String disclosure) {

    public static final String SCHEMA_VERSION =
            "chemicheck119-synthetic-replay-confirmation-v1";
    public static final String CONFIRMATION_TYPE = "SYNTHETIC_DEMO_CONFIRMATION";
    public static final String DISCLOSURE =
            "실제 대원 확인이 아닌 공개 합성 시연용 현장 확인입니다. 운영 판단에 사용할 수 없습니다.";
}
