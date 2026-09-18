package com.c2guard.bff.incident;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record IncidentBriefRequest(
        @NotNull @Valid IncidentAnalyzeRequest analysis,
        @Size(max = 2) List<@Size(max = 128) String> invalidatedConfirmationIds,
        Boolean reportedEvidenceConflict
) {
    public IncidentBriefRequest {
        invalidatedConfirmationIds = invalidatedConfirmationIds == null
                ? List.of() : List.copyOf(invalidatedConfirmationIds);
    }

    IncidentBriefCommand toCommand(String incidentId) {
        if (analysis.incidentId() != null && !incidentId.equals(analysis.incidentId())) {
            throw new IllegalArgumentException("경로 incidentId와 analysis incidentId가 다릅니다.");
        }
        IncidentAnalyzeRequest normalized = new IncidentAnalyzeRequest(
                incidentId, analysis.text(), analysis.inputType(), analysis.occurredAt(),
                analysis.location(), analysis.operationsContext(), analysis.plannedActions(),
                analysis.evidenceTopK());
        return new IncidentBriefCommand(normalized, invalidatedConfirmationIds,
                reportedEvidenceConflict);
    }
}
