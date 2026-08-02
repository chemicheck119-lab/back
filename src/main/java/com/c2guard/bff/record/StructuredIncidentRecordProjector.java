package com.c2guard.bff.record;

import com.c2guard.bff.confirmation.ConfirmationRole;
import com.c2guard.bff.confirmation.ConfirmationStatus;
import com.c2guard.bff.confirmation.SubstanceConfirmation;
import com.c2guard.bff.incident.IncidentAnalysisSnapshotStore;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

final class StructuredIncidentRecordProjector {

    Projection project(List<IncidentAnalysisSnapshotStore.Snapshot> analyses,
                       List<SubstanceConfirmation> confirmations) {
        SubstanceConfirmation incident = activeConfirmation(confirmations,
                ConfirmationRole.INCIDENT).orElse(null);
        SubstanceConfirmation facility = activeConfirmation(confirmations,
                ConfirmationRole.FACILITY).orElse(null);
        IncidentAnalysisSnapshotStore.Snapshot latest = analyses.stream()
                .max(Comparator.comparing(IncidentAnalysisSnapshotStore.Snapshot::createdAt)
                        .thenComparing(IncidentAnalysisSnapshotStore.Snapshot::analysisId))
                .orElse(null);
        return new Projection(
                incident == null ? null : trimToNull(incident.displayName()),
                incident == null ? null : trimToNull(incident.casNumber()),
                conflict(latest, facility));
    }

    private Optional<SubstanceConfirmation> activeConfirmation(
            List<SubstanceConfirmation> confirmations, ConfirmationRole role) {
        return confirmations.stream()
                .filter(value -> value.role() == role
                        && value.status() == ConfirmationStatus.ACTIVE)
                .max(Comparator.comparingLong(SubstanceConfirmation::revision));
    }

    private ConflictRisk conflict(IncidentAnalysisSnapshotStore.Snapshot latest,
                                  SubstanceConfirmation facility) {
        if (latest == null) return null;
        JsonNode review = latest.bffResponse().path("conflictReview");
        JsonNode result = review.path("result");
        if (!review.path("executed").asBoolean(false)
                || !"ORDINAL_SCREENING_RESULT".equals(result.path("kind").asText())) {
            return null;
        }
        return new ConflictRisk(
                latest.analysisId(),
                text(result, "incidentCas"),
                facility == null ? null : trimToNull(facility.displayName()),
                facility == null ? text(result, "facilityCas")
                        : trimToNull(facility.casNumber()),
                text(result, "ruleId"),
                text(result, "ruleVersion"),
                text(result, "severity"),
                text(result, "riskLevel"),
                text(result, "riskLevelKo"),
                text(result, "briefText"),
                result.path("expertReviewed").asBoolean(false),
                result.path("humanConfirmationRequired").asBoolean(true),
                strings(result.path("hazardCodes"), 100),
                strings(result.path("gasProducts"), 100));
    }

    private List<String> strings(JsonNode value, int limit) {
        if (!value.isArray()) return List.of();
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (JsonNode item : value) {
            String text = trimToNull(item.asText(null));
            if (text != null) unique.add(text);
            if (unique.size() >= limit) break;
        }
        return List.copyOf(new ArrayList<>(unique));
    }

    private String text(JsonNode value, String field) {
        return trimToNull(value.path(field).asText(null));
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    record Projection(
            String incidentSubstanceName,
            String incidentSubstanceCas,
            ConflictRisk conflictRisk
    ) {
    }

    record ConflictRisk(
            String analysisId,
            String incidentCas,
            String facilitySubstanceName,
            String facilitySubstanceCas,
            String ruleId,
            String ruleVersion,
            String severity,
            String riskLevel,
            String riskLevelKo,
            String briefText,
            boolean expertReviewed,
            boolean humanConfirmationRequired,
            List<String> hazardCodes,
            List<String> gasProducts
    ) {
    }
}
