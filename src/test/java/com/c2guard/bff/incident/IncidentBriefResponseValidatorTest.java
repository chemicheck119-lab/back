package com.c2guard.bff.incident;

import com.c2guard.bff.common.BffContractException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IncidentBriefResponseValidatorTest {

    private static final String REQUEST_ID = "REQ-VALIDATE-0001";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final IncidentBriefResponseValidator validator = new IncidentBriefResponseValidator();

    @Test
    void acceptsAWellFormedFinalResponse() {
        ObjectNode response = validResponse("final", "NEEDS_CONFIRMATION");

        assertEquals(response, validator.validate(response, REQUEST_ID));
    }

    @Test
    void rejectsAResponseWhoseBodyRequestIdDoesNotMatch() {
        ObjectNode response = validResponse("final", "NEEDS_CONFIRMATION");
        response.put("request_id", "REQ-OTHER");

        BffContractException error = assertThrows(BffContractException.class,
                () -> validator.validate(response, REQUEST_ID));
        assertEquals("MODEL_CONTRACT_VIOLATION", error.getCode());
    }

    @Test
    void rejectsAMinimalEnvelopeMissingRequiredFields() {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("schema_version", "action-brief-v1");

        assertThrows(BffContractException.class,
                () -> validator.validate(response, REQUEST_ID));
    }

    @Test
    void rejectsAFinalPhaseResponseWithoutCardsOrSources() {
        ObjectNode response = validResponse("final", "NEEDS_CONFIRMATION");
        response.remove("cards");

        assertThrows(BffContractException.class,
                () -> validator.validate(response, REQUEST_ID));
    }

    @Test
    void acceptsAnInitialPhaseResponseWithoutCardsOrSources() {
        ObjectNode response = validResponse("initial", "PENDING");
        response.remove("cards");
        response.remove("sources");

        assertEquals(response, validator.validate(response, REQUEST_ID));
    }

    private ObjectNode validResponse(String phase, String status) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("schema_version", "action-brief-v1");
        response.put("request_id", REQUEST_ID);
        response.put("phase", phase);
        response.put("status", status);
        ObjectNode confirmationState = response.putObject("confirmation_state");
        confirmationState.put("INCIDENT", true);
        confirmationState.put("FACILITY", true);
        ObjectNode ruleReview = response.putObject("rule_review");
        ruleReview.put("executed", false);
        response.putArray("cards");
        response.putArray("sources");
        return response;
    }
}
