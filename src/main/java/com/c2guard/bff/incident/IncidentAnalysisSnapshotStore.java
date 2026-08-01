package com.c2guard.bff.incident;

import com.c2guard.bff.common.BffContractException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class IncidentAnalysisSnapshotStore {

    private final ConcurrentMap<String, Snapshot> snapshots = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> latestAnalysisIds = new ConcurrentHashMap<>();

    public void save(String incidentId, String analysisId, String requestId,
                     JsonNode modelResponse, JsonNode bffResponse) {
        save(incidentId, analysisId, requestId, modelResponse, bffResponse,
                NullNode.getInstance());
    }

    public void save(String incidentId, String analysisId, String requestId,
                     JsonNode modelResponse, JsonNode bffResponse,
                     JsonNode agentResponse) {
        Snapshot snapshot = new Snapshot(incidentId, analysisId, requestId,
                modelResponse.deepCopy(), bffResponse.deepCopy(),
                agentResponse.deepCopy(), Instant.now());
        if (snapshots.putIfAbsent(analysisId, snapshot) != null) {
            throw new BffContractException(409, "INCIDENT_REFERENCE_CONFLICT",
                    "이미 저장된 분석 ID가 다시 반환됐습니다.", false);
        }
        latestAnalysisIds.put(incidentId, analysisId);
    }

    public Optional<Snapshot> find(String analysisId) {
        return Optional.ofNullable(snapshots.get(analysisId));
    }

    public Optional<Snapshot> findLatestForIncident(String incidentId) {
        String analysisId = latestAnalysisIds.get(incidentId);
        return analysisId == null ? Optional.empty() : find(analysisId);
    }

    public int size() {
        return snapshots.size();
    }

    public record Snapshot(
            String incidentId,
            String analysisId,
            String requestId,
            JsonNode modelResponse,
            JsonNode bffResponse,
            JsonNode agentResponse,
            Instant createdAt
    ) {
    }
}
