package com.c2guard.bff.incident;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncidentBriefRequestMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final IncidentBriefRequestMapper mapper = new IncidentBriefRequestMapper(objectMapper);

    @Test
    void wrapsAnalysisWithRevisionAtRoot() {
        ObjectNode analysis = objectMapper.createObjectNode().put("incident_id", "INC-1");

        ObjectNode request = mapper.map(analysis, 3L, List.of(), null);

        assertEquals(3L, request.path("revision").asLong());
        assertEquals("INC-1", request.path("analysis").path("incident_id").asText());
        assertFalse(request.has("invalidated_confirmation_ids"));
        assertFalse(request.has("reported_evidence_conflict"));
    }

    @Test
    void addsOptionalCancellationAndConflictFieldsAtRoot() {
        ObjectNode analysis = objectMapper.createObjectNode();

        ObjectNode request = mapper.map(analysis, 4L, List.of("CNF-DEMO-INCIDENT"), true);

        assertEquals(1, request.path("invalidated_confirmation_ids").size());
        assertEquals("CNF-DEMO-INCIDENT",
                request.path("invalidated_confirmation_ids").get(0).asText());
        assertTrue(request.path("reported_evidence_conflict").asBoolean());
    }
}
