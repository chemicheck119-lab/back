package com.c2guard.bff.incident;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IncidentAgentMemoryChecksumTest {

    @Test
    void matchesTheUpstreamPythonCanonicalJsonChecksum() {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode memory = objectMapper.createObjectNode();
        memory.put("schema_version", IncidentAgentResponseValidator.MEMORY_SCHEMA);
        memory.put("incident_id", "INC-CHECKSUM-1");
        memory.put("revision", 1);
        memory.put("request_state_fingerprint", "a".repeat(64));
        memory.put("runtime_state_fingerprint", "b".repeat(64));
        memory.put("status", "WAITING_FOR_HUMAN");
        memory.putArray("pending_inputs").add("INCIDENT_SUBSTANCE_CONFIRMATION");
        memory.put("last_analysis_state", "AWAITING_INCIDENT_CONFIRMATION");
        memory.put("last_analysis_id", "ANL-CHECKSUM-1");
        memory.put("last_run_id", "AGR-CHECKSUM-1");
        memory.putArray("history");
        memory.putNull("parent_memory_sha256");
        memory.put("memory_sha256", "0".repeat(64));
        memory.put("memory_trust_scope", "ORCHESTRATION_ONLY");
        memory.put("memory_can_trigger_rule", false);

        assertEquals("db0f6490ad3c782214c0a846bf398b895c3049e62913d36c099d6f847c2b0a83",
                new IncidentAgentMemoryChecksum(objectMapper).calculate(memory));
    }
}
