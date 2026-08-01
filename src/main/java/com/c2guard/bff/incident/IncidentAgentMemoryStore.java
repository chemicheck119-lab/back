package com.c2guard.bff.incident;

import com.c2guard.bff.common.BffContractException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class IncidentAgentMemoryStore {

    private final ConcurrentMap<String, StoredMemory> memories = new ConcurrentHashMap<>();
    private final IncidentAgentMemoryChecksum checksum;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public IncidentAgentMemoryStore(ObjectMapper objectMapper) {
        this(objectMapper, null, null, Clock.systemUTC());
    }

    @Autowired
    public IncidentAgentMemoryStore(ObjectMapper objectMapper, JdbcTemplate jdbcTemplate,
                                    TransactionTemplate transactionTemplate, Clock clock) {
        this.objectMapper = objectMapper;
        this.checksum = new IncidentAgentMemoryChecksum(objectMapper);
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    public Optional<StoredMemory> find(String incidentId) {
        if (jdbcTemplate != null) {
            List<StoredMemory> stored = jdbcTemplate.query("""
                            SELECT incident_id, revision, memory_sha256,
                                   parent_memory_sha256, memory_json, events_json,
                                   request_id, run_id, created_at
                            FROM incident_agent_memories
                            WHERE incident_id = ?
                            """, (resultSet, rowNumber) -> mapStoredMemory(resultSet),
                    incidentId);
            return stored.stream().findFirst().map(StoredMemory::copy);
        }
        StoredMemory stored = memories.get(incidentId);
        return stored == null ? Optional.empty() : Optional.of(stored.copy());
    }

    public StoredMemory compareAndSet(String incidentId, ObjectNode memory,
                                      ArrayNode events, String requestId,
                                      String runId) {
        validatePayload(incidentId, memory, runId);
        if (jdbcTemplate != null) {
            return compareAndSetDatabase(incidentId, memory, events, requestId, runId);
        }
        StoredMemory updated = memories.compute(incidentId, (key, current) -> {
            int revision = memory.path("revision").asInt();
            String memorySha256 = memory.path("memory_sha256").asText();
            JsonNode parentNode = memory.get("parent_memory_sha256");
            String parentSha256 = parentNode == null || parentNode.isNull()
                    ? null : parentNode.asText();

            if (current == null) {
                if (revision != 1 || parentSha256 != null) {
                    throw conflict();
                }
            } else if (revision != current.revision() + 1
                    || !current.memorySha256().equals(parentSha256)) {
                throw conflict();
            }

            return new StoredMemory(incidentId, revision, memorySha256,
                    parentSha256, memory.deepCopy(), events.deepCopy(),
                    requestId, runId, clock.instant());
        });
        return updated.copy();
    }

    public int size() {
        if (jdbcTemplate != null) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM incident_agent_memories", Integer.class);
            return count == null ? 0 : count;
        }
        return memories.size();
    }

    private StoredMemory compareAndSetDatabase(String incidentId, ObjectNode memory,
                                                ArrayNode events, String requestId,
                                                String runId) {
        try {
            StoredMemory stored = transactionTemplate.execute(status -> {
                List<StoredMemory> currentRows = jdbcTemplate.query("""
                                SELECT incident_id, revision, memory_sha256,
                                       parent_memory_sha256, memory_json, events_json,
                                       request_id, run_id, created_at
                                FROM incident_agent_memories
                                WHERE incident_id = ?
                                FOR UPDATE
                                """, (resultSet, rowNumber) -> mapStoredMemory(resultSet),
                        incidentId);
                StoredMemory current = currentRows.isEmpty() ? null : currentRows.get(0);
                int revision = memory.path("revision").asInt();
                String memorySha256 = memory.path("memory_sha256").asText();
                JsonNode parentNode = memory.get("parent_memory_sha256");
                String parentSha256 = parentNode == null || parentNode.isNull()
                        ? null : parentNode.asText();

                if (current == null) {
                    if (revision != 1 || parentSha256 != null) {
                        throw conflict();
                    }
                } else if (revision != current.revision() + 1
                        || !current.memorySha256().equals(parentSha256)) {
                    throw conflict();
                }

                Instant createdAt = clock.instant();
                StoredMemory updated = new StoredMemory(incidentId, revision,
                        memorySha256, parentSha256, memory.deepCopy(), events.deepCopy(),
                        requestId, runId, createdAt);
                if (current == null) {
                    jdbcTemplate.update("""
                                    INSERT INTO incident_agent_memories (
                                        incident_id, revision, memory_sha256,
                                        parent_memory_sha256, memory_json, events_json,
                                        request_id, run_id, created_at
                                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                                    """, incidentId, revision, memorySha256,
                            parentSha256, json(memory), json(events), requestId, runId,
                            OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC));
                } else {
                    int changed = jdbcTemplate.update("""
                                    UPDATE incident_agent_memories
                                    SET revision = ?, memory_sha256 = ?,
                                        parent_memory_sha256 = ?, memory_json = ?,
                                        events_json = ?, request_id = ?, run_id = ?,
                                        created_at = ?
                                    WHERE incident_id = ? AND revision = ?
                                    """, revision, memorySha256, parentSha256,
                            json(memory), json(events), requestId, runId,
                            OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC),
                            incidentId, current.revision());
                    if (changed != 1) {
                        throw conflict();
                    }
                }
                return updated;
            });
            if (stored == null) {
                throw new IllegalStateException("agent memory transaction returned no value");
            }
            return stored.copy();
        } catch (DataIntegrityViolationException conflict) {
            throw conflict();
        }
    }

    private StoredMemory mapStoredMemory(java.sql.ResultSet resultSet)
            throws java.sql.SQLException {
        OffsetDateTime createdAt = resultSet.getObject("created_at", OffsetDateTime.class);
        return new StoredMemory(
                resultSet.getString("incident_id"),
                resultSet.getInt("revision"),
                resultSet.getString("memory_sha256"),
                resultSet.getString("parent_memory_sha256"),
                objectNode(resultSet.getString("memory_json")),
                arrayNode(resultSet.getString("events_json")),
                resultSet.getString("request_id"),
                resultSet.getString("run_id"),
                createdAt.toInstant());
    }

    private String json(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            throw new IllegalStateException("검증된 agent memory를 직렬화하지 못했습니다.", error);
        }
    }

    private ObjectNode objectNode(String json) {
        try {
            return (ObjectNode) objectMapper.readTree(json);
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            throw new IllegalStateException("저장된 agent memory를 읽지 못했습니다.", error);
        }
    }

    private ArrayNode arrayNode(String json) {
        try {
            return (ArrayNode) objectMapper.readTree(json);
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            throw new IllegalStateException("저장된 agent events를 읽지 못했습니다.", error);
        }
    }

    private void validatePayload(String incidentId, ObjectNode memory, String runId) {
        if (!incidentId.equals(memory.path("incident_id").asText())
                || !runId.equals(memory.path("last_run_id").asText())
                || !memory.path("memory_sha256").asText().equals(checksum.calculate(memory))) {
            throw new BffContractException(422, "MODEL_CONTRACT_VIOLATION",
                    "저장하려는 agent memory가 검증된 응답과 일치하지 않습니다.", false);
        }
    }

    private static BffContractException conflict() {
        return new BffContractException(409, "AGENT_MEMORY_CONFLICT",
                "더 최신 사고 에이전트 memory가 이미 저장되어 있습니다.", false);
    }

    public record StoredMemory(
            String incidentId,
            int revision,
            String memorySha256,
            String parentMemorySha256,
            ObjectNode memory,
            ArrayNode events,
            String requestId,
            String runId,
            Instant createdAt
    ) {
        StoredMemory copy() {
            return new StoredMemory(incidentId, revision, memorySha256,
                    parentMemorySha256, memory.deepCopy(), events.deepCopy(),
                    requestId, runId, createdAt);
        }
    }
}
