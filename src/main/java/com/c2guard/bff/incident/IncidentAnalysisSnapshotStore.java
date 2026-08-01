package com.c2guard.bff.incident;

import com.c2guard.bff.common.BffContractException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class IncidentAnalysisSnapshotStore {

    private final ConcurrentMap<String, Snapshot> snapshots = new ConcurrentHashMap<>();

    public void save(String incidentId, String analysisId, String requestId,
                     JsonNode modelResponse, JsonNode bffResponse) {
        Snapshot snapshot = new Snapshot(incidentId, analysisId, requestId,
                modelResponse.deepCopy(), bffResponse.deepCopy(), Instant.now());
        if (snapshots.putIfAbsent(analysisId, snapshot) != null) {
            throw new BffContractException(409, "INCIDENT_REFERENCE_CONFLICT",
                    "이미 저장된 분석 ID가 다시 반환됐습니다.", false);
        }
    }

    public Optional<Snapshot> find(String analysisId) {
        return Optional.ofNullable(snapshots.get(analysisId));
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
            Instant createdAt
    ) {
    }
}
