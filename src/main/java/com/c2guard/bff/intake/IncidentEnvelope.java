package com.c2guard.bff.intake;

import java.time.Instant;
import java.util.List;

public record IncidentEnvelope(
        String schemaVersion,
        String incidentId,
        String sourceEventId,
        String idempotencyKey,
        Instant receivedAt,
        Instant occurredAt,
        String stationId,
        String stationDisplayName,
        String facilityName,
        String addressText,
        IncidentLocation location,
        String reportText,
        IncidentSourceType sourceType,
        IncidentDataClassification dataClassification,
        String sourceProvider,
        String sourceSchemaVersion,
        String requestId,
        boolean containsPersonalInformation,
        String disclosure,
        List<IncidentDatasetReference> datasetReferences) {

    public IncidentEnvelope {
        datasetReferences = List.copyOf(datasetReferences);
    }
}
