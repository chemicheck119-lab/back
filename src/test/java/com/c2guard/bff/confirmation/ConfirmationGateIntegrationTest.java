package com.c2guard.bff.confirmation;

import com.c2guard.integration.model.ModelApiClient;
import com.c2guard.integration.model.ModelApiResponse;
import com.c2guard.security.SignedSessionTokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.c2guard.security.BffTestSession.responder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ConfirmationGateIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SignedSessionTokenService tokenService;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ConfirmationIdGenerator idGenerator;

    @MockBean
    private ModelApiClient modelApiClient;

    @Test
    void injectsBothSavedRolesIntoTheNextAnalysisRequest() throws Exception {
        String incidentId = "INC-GATE-PAIR";
        String analysisRequestId = "REQ-GATE-ANALYZE";
        when(idGenerator.nextId()).thenReturn("CFM-INC-0001", "CFM-FAC-0001");
        save(incidentId, "REQ-GATE-INCIDENT", incidentFixture());
        save(incidentId, "REQ-GATE-FACILITY", facilityFixture());
        stubModelResponse(analysisRequestId, incidentId, "ANL-GATE-PAIR");

        mockMvc.perform(post("/api/c2guard/v1/incidents/analyze")
                        .cookie(responder(tokenService, incidentId))
                        .header("X-Request-Id", analysisRequestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(analysisFixture(incidentId)))
                .andExpect(status().isOk());

        ArgumentCaptor<JsonNode> request = ArgumentCaptor.forClass(JsonNode.class);
        verify(modelApiClient).analyzeIncident(request.capture(), eq(analysisRequestId));
        JsonNode modelRequest = request.getValue();
        JsonNode expected = objectMapper.readTree(Files.readString(Path.of(
                "src/test/resources/fixtures/model/incident_confirmed_request.json")));
        assertEquals(expected.path("confirmed_incident_substance"),
                modelRequest.path("confirmed_incident_substance"));
        assertEquals(expected.path("confirmed_facility_substance"),
                modelRequest.path("confirmed_facility_substance"));
        assertEquals("CONFIRMED_PRESENT", modelRequest.path("confirmed_incident_substance")
                .path("presence_status").asText());
    }

    @Test
    void oneSavedRoleNeverCreatesTheMissingConfirmedObject() throws Exception {
        String incidentId = "INC-GATE-ONE-SIDE";
        String analysisRequestId = "REQ-GATE-ONE-SIDE";
        when(idGenerator.nextId()).thenReturn("CFM-ONLY-INCIDENT");
        save(incidentId, "REQ-GATE-ONLY-CONFIRM", incidentFixture());
        stubModelResponse(analysisRequestId, incidentId, "ANL-GATE-ONE-SIDE");

        mockMvc.perform(post("/api/c2guard/v1/incidents/analyze")
                        .cookie(responder(tokenService, incidentId))
                        .header("X-Request-Id", analysisRequestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(analysisFixture(incidentId)))
                .andExpect(status().isOk());

        ArgumentCaptor<JsonNode> request = ArgumentCaptor.forClass(JsonNode.class);
        verify(modelApiClient).analyzeIncident(request.capture(), eq(analysisRequestId));
        assertTrue(request.getValue().has("confirmed_incident_substance"));
        assertFalse(request.getValue().has("confirmed_facility_substance"));
    }

    private void save(String incidentId, String requestId, String body) throws Exception {
        mockMvc.perform(post("/api/c2guard/v1/incidents/" + incidentId + "/confirmations")
                        .cookie(responder(tokenService, incidentId))
                        .header("X-Request-Id", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    private void stubModelResponse(String requestId, String incidentId, String analysisId)
            throws Exception {
        ObjectNode response = (ObjectNode) objectMapper.readTree(Files.readString(Path.of(
                "src/test/resources/fixtures/model/incident_unconfirmed_response.json")));
        response.put("request_id", requestId);
        response.put("incident_id", incidentId);
        response.put("analysis_id", analysisId);
        when(modelApiClient.analyzeIncident(any(JsonNode.class), eq(requestId)))
                .thenReturn(new ModelApiResponse(requestId, response));
    }

    private String analysisFixture(String incidentId) throws Exception {
        ObjectNode request = (ObjectNode) objectMapper.readTree(Files.readString(Path.of(
                "contracts/examples/bff/incident_analyze_request.json")));
        request.put("incidentId", incidentId);
        return objectMapper.writeValueAsString(request);
    }

    private String incidentFixture() throws Exception {
        ObjectNode request = (ObjectNode) objectMapper.readTree(Files.readString(Path.of(
                "contracts/examples/bff/confirmation_request.json")));
        request.put("observedAt", "2026-01-15T14:25:00+09:00");
        return objectMapper.writeValueAsString(request);
    }

    private String facilityFixture() throws Exception {
        ObjectNode request = (ObjectNode) objectMapper.readTree(Files.readString(Path.of(
                "contracts/examples/bff/confirmation_request.json")));
        request.put("role", "FACILITY");
        request.put("casNumber", "7647-01-0");
        request.put("displayName", "염산");
        request.put("confirmationBasis", "SITE_MSDS");
        request.put("observedAt", "2026-01-15T14:27:00+09:00");
        return objectMapper.writeValueAsString(request);
    }
}
