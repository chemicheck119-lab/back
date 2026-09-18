package com.c2guard.bff.incident;

import com.c2guard.integration.model.ModelApiClient;
import com.c2guard.integration.model.ModelApiResponse;
import com.c2guard.security.SignedSessionTokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.c2guard.security.BffTestSession.responder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class IncidentBriefControllerTest {

    private static final String PATH = "/api/c2guard/v1/incidents/brief";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SignedSessionTokenService tokenService;

    @MockBean
    private ModelApiClient modelApiClient;

    @Test
    void returnsTheModelResponseUnchangedForAnAuthenticatedRequest() throws Exception {
        String requestId = "REQ-BRIEF-EXAMPLE-0001";
        JsonNode analysis = objectMapper.readTree(
                Files.readString(Path.of("contracts/examples/bff/incident_analyze_request.json")));
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.set("analysis", analysis);

        confirmSubstance("INCIDENT", "7681-52-9", "차아염소산나트륨");
        confirmSubstance("FACILITY", "7647-01-0", "염산");

        ObjectNode modelResponse = objectMapper.createObjectNode();
        modelResponse.put("schema_version", "action-brief-v1");
        modelResponse.put("request_id", requestId);
        modelResponse.put("phase", "final");
        modelResponse.put("status", "NEEDS_CONFIRMATION");
        ObjectNode confirmationState = modelResponse.putObject("confirmation_state");
        confirmationState.put("INCIDENT", true);
        confirmationState.put("FACILITY", true);
        ObjectNode ruleReview = modelResponse.putObject("rule_review");
        ruleReview.put("executed", false);
        modelResponse.putArray("cards");
        modelResponse.putArray("sources");
        when(modelApiClient.briefIncident(any(JsonNode.class), eq(requestId)))
                .thenReturn(new ModelApiResponse(requestId, modelResponse));

        mockMvc.perform(post(PATH)
                        .cookie(responder(tokenService, "INC-EXAMPLE-0001"))
                        .header("X-Request-Id", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", requestId))
                .andExpect(content().json(modelResponse.toString(), true));
    }

    @Test
    void rejectsRequestsWithoutAnAuthenticatedSession() throws Exception {
        JsonNode analysis = objectMapper.readTree(
                Files.readString(Path.of("contracts/examples/bff/incident_analyze_request.json")));
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.set("analysis", analysis);

        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody.toString()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(modelApiClient);
    }

    @Test
    void rejectsInvalidClientInputBeforeCallingTheModel() throws Exception {
        String requestId = "REQ-BRIEF-INVALID-0001";

        mockMvc.perform(post(PATH)
                        .cookie(responder(tokenService, "INC-EXAMPLE-0001"))
                        .header("X-Request-Id", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"analysis\":{\"text\":\"   \"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        verifyNoInteractions(modelApiClient);
    }

    private void confirmSubstance(String role, String casNumber, String displayName)
            throws Exception {
        ObjectNode confirmation = objectMapper.createObjectNode();
        confirmation.put("role", role);
        confirmation.put("casNumber", casNumber);
        confirmation.put("displayName", displayName);
        confirmation.put("confirmationBasis", "CONTAINER_LABEL");
        confirmation.put("observedAt", "2026-07-31T14:25:00+09:00");

        mockMvc.perform(post("/api/c2guard/v1/incidents/INC-EXAMPLE-0001/confirmations")
                        .cookie(responder(tokenService, "INC-EXAMPLE-0001"))
                        .header("X-Request-Id", "REQ-CONFIRM-" + role)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmation.toString()))
                .andExpect(status().isCreated());
    }
}
