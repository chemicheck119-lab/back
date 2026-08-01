package com.c2guard.bff.incident;

import com.c2guard.bff.common.BffContractException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class IncidentAnalysisSnapshotStore {

    private final ConcurrentMap<String, Snapshot> snapshots = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> latestAnalysisIds = new ConcurrentHashMap<>();
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public IncidentAnalysisSnapshotStore() {
        this(null, new ObjectMapper(), Clock.systemUTC());
    }

    @Autowired
    public IncidentAnalysisSnapshotStore(JdbcTemplate jdbcTemplate,
                                         ObjectMapper objectMapper, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void save(String incidentId, String analysisId, String requestId,
                     JsonNode modelResponse, JsonNode bffResponse) {
        save(incidentId, analysisId, requestId, modelResponse, bffResponse,
                NullNode.getInstance());
    }

    public void save(String incidentId, String analysisId, String requestId,
                     JsonNode modelResponse, JsonNode bffResponse,
                     JsonNode agentResponse) {
        if (jdbcTemplate != null) {
            saveDatabase(incidentId, analysisId, requestId, modelResponse,
                    bffResponse, agentResponse);
            return;
        }
        Snapshot snapshot = new Snapshot(incidentId, analysisId, requestId,
                modelResponse.deepCopy(), bffResponse.deepCopy(),
                agentResponse.deepCopy(), clock.instant());
        if (snapshots.putIfAbsent(analysisId, snapshot) != null) {
            throw new BffContractException(409, "INCIDENT_REFERENCE_CONFLICT",
                    "이미 저장된 분석 ID가 다시 반환됐습니다.", false);
        }
        latestAnalysisIds.put(incidentId, analysisId);
    }

    public Optional<Snapshot> find(String analysisId) {
        if (jdbcTemplate != null) {
            return query("""
                    SELECT incident_id, analysis_id, request_id,
                           model_response_json, bff_response_json,
                           agent_response_json, created_at
                    FROM incident_analysis_snapshots
                    WHERE analysis_id = ?
                    """, analysisId).stream().findFirst();
        }
        return Optional.ofNullable(snapshots.get(analysisId));
    }

    public Optional<Snapshot> findLatestForIncident(String incidentId) {
        if (jdbcTemplate != null) {
            return query("""
                    SELECT incident_id, analysis_id, request_id,
                           model_response_json, bff_response_json,
                           agent_response_json, created_at
                    FROM incident_analysis_snapshots
                    WHERE incident_id = ?
                    ORDER BY created_at DESC, analysis_id DESC
                    LIMIT 1
                    """, incidentId).stream().findFirst();
        }
        String analysisId = latestAnalysisIds.get(incidentId);
        return analysisId == null ? Optional.empty() : find(analysisId);
    }

    public int size() {
        if (jdbcTemplate != null) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM incident_analysis_snapshots", Integer.class);
            return count == null ? 0 : count;
        }
        return snapshots.size();
    }

    private void saveDatabase(String incidentId, String analysisId, String requestId,
                              JsonNode modelResponse, JsonNode bffResponse,
                              JsonNode agentResponse) {
        try {
            jdbcTemplate.update("""
                            INSERT INTO incident_analysis_snapshots (
                                analysis_id, incident_id, request_id,
                                model_response_json, bff_response_json,
                                agent_response_json, created_at
                            ) VALUES (?, ?, ?, ?, ?, ?, ?)
                            """, analysisId, incidentId, requestId,
                    json(modelResponse), json(bffResponse), json(agentResponse),
                    OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        } catch (DataIntegrityViolationException duplicate) {
            throw new BffContractException(409, "INCIDENT_REFERENCE_CONFLICT",
                    "이미 저장된 분석 ID가 다시 반환됐습니다.", false);
        }
    }

    private List<Snapshot> query(String sql, String value) {
        return jdbcTemplate.query(sql, (resultSet, rowNumber) -> {
            OffsetDateTime createdAt = resultSet.getObject(
                    "created_at", OffsetDateTime.class);
            return new Snapshot(
                    resultSet.getString("incident_id"),
                    resultSet.getString("analysis_id"),
                    resultSet.getString("request_id"),
                    jsonNode(resultSet.getString("model_response_json")),
                    jsonNode(resultSet.getString("bff_response_json")),
                    jsonNode(resultSet.getString("agent_response_json")),
                    createdAt.toInstant());
        }, value);
    }

    private String json(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            throw new IllegalStateException("분석 snapshot을 직렬화하지 못했습니다.", error);
        }
    }

    private JsonNode jsonNode(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            throw new IllegalStateException("저장된 분석 snapshot을 읽지 못했습니다.", error);
        }
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
