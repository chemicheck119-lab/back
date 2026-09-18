package com.c2guard.bff.incident;

import com.c2guard.bff.common.BffContractException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IncidentBriefResponseValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final IncidentBriefResponseValidator validator = new IncidentBriefResponseValidator();

    @Test
    void rejectsResponseWithOnlySchemaVersion() {
        ObjectNode response = objectMapper.createObjectNode()
                .put("schema_version", "action-brief-v1");

        BffContractException error = assertThrows(BffContractException.class,
                () -> validator.validate(response, "REQ-1", "INC-1", 1));

        assertEquals("MODEL_CONTRACT_VIOLATION", error.getCode());
        assertEquals(422, error.getStatus());
    }

    @Test
    void rejectsResponseWhenConfirmationStateIsNotVerified() {
        ObjectNode response = validResponse();
        response.with("confirmation_state").put("FACILITY", false);

        BffContractException error = assertThrows(BffContractException.class,
                () -> validator.validate(response, "REQ-1", "INC-1", 1));

        assertEquals("MODEL_CONTRACT_VIOLATION", error.getCode());
    }

    @Test
    void acceptsResponseBoundToRequestContext() {
        ObjectNode response = validResponse();

        validator.validate(response, "REQ-1", "INC-1", 1);
    }

    private ObjectNode validResponse() {
        ObjectNode response = objectMapper.createObjectNode()
                .put("schema_version", "action-brief-v1")
                .put("request_id", "REQ-1")
                .put("incident_id", "INC-1")
                .put("revision", 1)
                .put("state_fingerprint", "fingerprint")
                .put("phase", "final")
                .put("status", "COMPLETED")
                .put("summary", "검토용 행동 카드");
        response.putArray("cards");
        response.putArray("missing_information");
        response.putArray("sources");
        response.putObject("confirmation_state")
                .put("INCIDENT", true)
                .put("FACILITY", true);
        response.putObject("versions");
        return response;
    }
}
