package com.c2guard.bff.incident;

import com.c2guard.bff.common.BffContractException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IncidentAgentResponseValidatorTest {

    private static final String REQUEST_ID = "REQ-VALIDATOR-1";
    private static final String INCIDENT_ID = "INC-VALIDATOR-1";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final IncidentAgentResponseValidator validator =
            new IncidentAgentResponseValidator(objectMapper);

    @Test
    void acceptsAValidWaitingResponseAndPreservesTheAnalysis() {
        ObjectNode analysis = analysis();
        ObjectNode response = IncidentAgentTestResponse.withAnalysis(objectMapper,
                analysis, REQUEST_ID, INCIDENT_ID, null);

        ValidatedIncidentAgentResponse validated = validator.validate(response,
                REQUEST_ID, INCIDENT_ID);

        assertEquals("WAITING_FOR_HUMAN", validated.status());
        assertEquals(analysis, validated.analysis());
        assertEquals(1, validated.memory().path("revision").asInt());
        assertFalse(validated.retryable());
    }

    @Test
    void rejectsTamperedMemoryAndUnsafeAgentFlags() {
        ObjectNode tampered = IncidentAgentTestResponse.withAnalysis(objectMapper,
                analysis(), REQUEST_ID, INCIDENT_ID, null);
        ((ObjectNode) tampered.path("memory")).put("revision", 99);

        BffContractException checksumError = assertThrows(BffContractException.class,
                () -> validator.validate(tampered, REQUEST_ID, INCIDENT_ID));
        assertEquals(422, checksumError.getStatus());
        assertEquals("MODEL_CONTRACT_VIOLATION", checksumError.getCode());

        ObjectNode unsafe = IncidentAgentTestResponse.withAnalysis(objectMapper,
                analysis(), REQUEST_ID, INCIDENT_ID, null);
        unsafe.put("autonomous_risk_decision_allowed", true);
        assertThrows(BffContractException.class,
                () -> validator.validate(unsafe, REQUEST_ID, INCIDENT_ID));
    }

    @Test
    void validatesRetryableFailureWithoutAnAnalysis() {
        ObjectNode response = IncidentAgentTestResponse.failed(objectMapper,
                REQUEST_ID, INCIDENT_ID, null, "FAILED_RETRYABLE");

        ValidatedIncidentAgentResponse validated = validator.validate(response,
                REQUEST_ID, INCIDENT_ID);

        assertEquals("FAILED_RETRYABLE", validated.status());
        assertNull(validated.analysis());
        assertEquals(true, validated.retryable());
    }

    private ObjectNode analysis() {
        ObjectNode analysis = objectMapper.createObjectNode();
        analysis.put("state", "AWAITING_SUBSTANCE_CONFIRMATION");
        analysis.put("analysis_id", "ANL-VALIDATOR-1");
        return analysis;
    }
}
