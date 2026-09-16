package com.c2guard.bff.record;

import java.time.OffsetDateTime;
import java.util.List;

public record RecordDetailResponse(
        String schemaVersion,
        String requestId,
        String recordId,
        String incidentId,
        OffsetDateTime conversationStartedAt,
        OffsetDateTime savedAt,
        String facilityName,
        String facilityAddress,
        String incidentSubstanceName,
        String incidentSubstanceCas,
        String briefApplicationStatus,
        List<String> performedActions,
        List<String> additionalFactors,
        String finalResponseOutcome,
        RecordConflictRisk conflictRisk,
        List<RecordMessage> messages
) {
    public RecordDetailResponse(String requestId, RecordDetail detail) {
        this("chemicheck119-dashboard-bff-v1", requestId, detail.recordId(), detail.incidentId(),
                detail.conversationStartedAt(), detail.savedAt(), detail.facilityName(),
                detail.facilityAddress(), detail.incidentSubstanceName(),
                detail.incidentSubstanceCas(), detail.briefApplicationStatus(),
                detail.performedActions(), detail.additionalFactors(),
                detail.finalResponseOutcome(), detail.conflictRisk(), detail.messages());
    }
}
