package com.c2guard.bff.demolog;

import java.time.OffsetDateTime;
import java.util.List;

public record DemoIncidentLogPageResponse(
        String schemaVersion,
        String requestId,
        String datasetVersion,
        String dataClassification,
        boolean operationalRecord,
        String disclosure,
        Station station,
        int totalElements,
        int offset,
        int limit,
        List<LogEntry> logs
) {
    public static final String SCHEMA_VERSION =
            "chemicheck119-synthetic-demo-logs-v1";

    public record Station(String stationId, String stationDisplayName, String region) {
    }

    public record LogEntry(
            String demoLogId,
            String scenarioId,
            OffsetDateTime occurredAt,
            String facilityName,
            String facilityAddress,
            String incidentSubstanceName,
            String incidentSubstanceCas,
            String conflictSubstanceName,
            String conflictSubstanceCas,
            String ruleId,
            String ruleVersion,
            String severity,
            String riskLevel,
            String riskLevelKo,
            List<String> hazardCodes,
            List<String> gasProducts,
            String riskSummary,
            String performedAction,
            String briefApplicationStatus,
            String additionalFactor,
            String finalResponseOutcome,
            String dataClassification,
            boolean operationalRecord,
            String sourceName,
            String sourceUrl
    ) {
    }
}
