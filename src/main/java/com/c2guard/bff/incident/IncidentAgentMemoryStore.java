package com.c2guard.bff.incident;

import com.c2guard.bff.common.BffContractException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class IncidentAgentMemoryStore {

    private final ConcurrentMap<String, StoredMemory> memories = new ConcurrentHashMap<>();
    private final IncidentAgentMemoryChecksum checksum;

    public IncidentAgentMemoryStore(ObjectMapper objectMapper) {
        this.checksum = new IncidentAgentMemoryChecksum(objectMapper);
    }

    public Optional<StoredMemory> find(String incidentId) {
        StoredMemory stored = memories.get(incidentId);
        return stored == null ? Optional.empty() : Optional.of(stored.copy());
    }

    public StoredMemory compareAndSet(String incidentId, ObjectNode memory,
                                      ArrayNode events, String requestId,
                                      String runId) {
        validatePayload(incidentId, memory, runId);
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
                    requestId, runId, Instant.now());
        });
        return updated.copy();
    }

    public int size() {
        return memories.size();
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
