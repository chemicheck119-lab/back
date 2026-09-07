package com.c2guard.bff.confirmation;

import com.c2guard.integration.model.ModelApiClient;
import com.c2guard.integration.model.ModelApiResponse;
import com.c2guard.bff.incident.IncidentAgentTestResponse;
import com.c2guard.bff.incident.IncidentAnalysisSnapshotStore;
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
import java.time.OffsetDateTime;

import static com.c2guard.security.BffTestSession.responder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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

    @Autowired
    private ConfirmationStore confirmationStore;

    @Autowired
    private IncidentAnalysisSnapshotStore snapshotStore;

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
        verify(modelApiClient).stepIncidentAgent(request.capture(), eq(analysisRequestId));
        JsonNode modelRequest = request.getValue().path("analysis");
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
        verify(modelApiClient).stepIncidentAgent(request.capture(), eq(analysisRequestId));
        JsonNode analysis = request.getValue().path("analysis");
        assertTrue(analysis.has("confirmed_incident_substance"));
        assertFalse(analysis.has("confirmed_facility_substance"));
    }

    @Test
    void reanalysisSendsBothTheLatestMemoryAndNewAuthoritativeConfirmation()
            throws Exception {
        String incidentId = "INC-GATE-MEMORY-REFRESH";
        String firstRequestId = "REQ-GATE-MEMORY-FIRST";
        String secondRequestId = "REQ-GATE-MEMORY-SECOND";
        when(idGenerator.nextId()).thenReturn("CFM-GATE-MEMORY-INCIDENT");

        ObjectNode firstAnalysis = modelResponse(firstRequestId, incidentId,
                "ANL-GATE-MEMORY-FIRST");
        ObjectNode firstAgent = IncidentAgentTestResponse.withAnalysis(objectMapper,
                firstAnalysis, firstRequestId, incidentId, null);
        ObjectNode secondAnalysis = modelResponse(secondRequestId, incidentId,
                "ANL-GATE-MEMORY-SECOND");
        secondAnalysis.put("state", "AWAITING_FACILITY_CONFIRMATION");
        ((ObjectNode) secondAnalysis.path("confirmation_gate"))
                .put("incident_confirmed", true);
        ((com.fasterxml.jackson.databind.node.ArrayNode) secondAnalysis
                .path("conflict_review").path("missing_confirmations"))
                .removeAll().add("facility_cas");
        ObjectNode secondAgent = IncidentAgentTestResponse.withAnalysis(objectMapper,
                secondAnalysis, secondRequestId, incidentId, firstAgent.path("memory"));
        when(modelApiClient.stepIncidentAgent(any(JsonNode.class), eq(firstRequestId)))
                .thenReturn(new ModelApiResponse(firstRequestId, firstAgent));
        when(modelApiClient.stepIncidentAgent(any(JsonNode.class), eq(secondRequestId)))
                .thenReturn(new ModelApiResponse(secondRequestId, secondAgent));

        String analysisBody = analysisFixture(incidentId);
        mockMvc.perform(post("/api/c2guard/v1/incidents/analyze")
                        .cookie(responder(tokenService, incidentId))
                        .header("X-Request-Id", firstRequestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(analysisBody))
                .andExpect(status().isOk());
        save(incidentId, "REQ-GATE-MEMORY-CONFIRM", incidentFixture());
        mockMvc.perform(post("/api/c2guard/v1/incidents/analyze")
                        .cookie(responder(tokenService, incidentId))
                        .header("X-Request-Id", secondRequestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(analysisBody))
                .andExpect(status().isOk());

        ArgumentCaptor<JsonNode> requests = ArgumentCaptor.forClass(JsonNode.class);
        verify(modelApiClient, times(2)).stepIncidentAgent(requests.capture(),
                org.mockito.ArgumentMatchers.anyString());
        JsonNode secondRequest = requests.getAllValues().get(1);
        assertEquals(firstAgent.path("memory"), secondRequest.path("memory"));
        assertEquals("CFM-GATE-MEMORY-INCIDENT", secondRequest.path("analysis")
                .path("confirmed_incident_substance").path("confirmation_id").asText());
        assertFalse(secondRequest.path("analysis").has("confirmed_facility_substance"));
    }

    @Test
    void doesNotReuseSnapshotCreatedBeforeANewConfirmation() throws Exception {
        String incidentId = "INC-GATE-STALE-SNAPSHOT";
        String firstRequestId = "REQ-GATE-STALE-FIRST";
        String secondRequestId = "REQ-GATE-STALE-SECOND";
        when(idGenerator.nextId()).thenReturn("CFM-GATE-STALE-INCIDENT");

        ObjectNode firstAnalysis = modelResponse(firstRequestId, incidentId,
                "ANL-GATE-STALE-FIRST");
        ObjectNode firstAgent = IncidentAgentTestResponse.withAnalysis(objectMapper,
                firstAnalysis, firstRequestId, incidentId, null);
        ObjectNode secondAgent = IncidentAgentTestResponse.withoutAnalysis(objectMapper,
                secondRequestId, incidentId, firstAgent.path("memory"));
        when(modelApiClient.stepIncidentAgent(any(JsonNode.class), eq(firstRequestId)))
                .thenReturn(new ModelApiResponse(firstRequestId, firstAgent));
        when(modelApiClient.stepIncidentAgent(any(JsonNode.class), eq(secondRequestId)))
                .thenReturn(new ModelApiResponse(secondRequestId, secondAgent));

        String analysisBody = analysisFixture(incidentId);
        mockMvc.perform(post("/api/c2guard/v1/incidents/analyze")
                        .cookie(responder(tokenService, incidentId))
                        .header("X-Request-Id", firstRequestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(analysisBody))
                .andExpect(status().isOk());
        save(incidentId, "REQ-GATE-STALE-CONFIRM", incidentFixture());

        mockMvc.perform(post("/api/c2guard/v1/incidents/analyze")
                        .cookie(responder(tokenService, incidentId))
                        .header("X-Request-Id", secondRequestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(analysisBody))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("MODEL_CONTRACT_VIOLATION"));
    }

    @Test
    void rejectsAnAnalysisWhenConfirmationChangesDuringTheModelCall() throws Exception {
        String incidentId = "INC-GATE-CONCURRENT-CORRECTION";
        String requestId = "REQ-GATE-CONCURRENT-CORRECTION";
        String analysisId = "ANL-GATE-CONCURRENT-CORRECTION";
        when(idGenerator.nextId()).thenReturn("CFM-GATE-CONCURRENT-CORRECTION");
        ObjectNode analysis = modelResponse(requestId, incidentId, analysisId);
        ObjectNode agent = IncidentAgentTestResponse.withAnalysis(objectMapper,
                analysis, requestId, incidentId, null);
        when(modelApiClient.stepIncidentAgent(any(JsonNode.class), eq(requestId)))
                .thenAnswer(ignored -> {
                    confirmationStore.save(new ConfirmationSaveCommand(
                            incidentId, ConfirmationRole.INCIDENT, "7681-52-9",
                            "차아염소산나트륨", ConfirmationBasis.CONTAINER_LABEL,
                            OffsetDateTime.parse("2026-01-15T14:25:00+09:00"),
                            "responder-1", "fire-station-119",
                            "REQ-GATE-CONCURRENT-CONFIRM"));
                    return new ModelApiResponse(requestId, agent);
                });

        mockMvc.perform(post("/api/c2guard/v1/incidents/analyze")
                        .cookie(responder(tokenService, incidentId))
                        .header("X-Request-Id", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(analysisFixture(incidentId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code")
                        .value("INCIDENT_REFERENCE_CONFLICT"))
                .andExpect(jsonPath("$.error.retryable").value(true));

        assertTrue(snapshotStore.find(analysisId).isEmpty());
    }

    @Test
    void cancelledFacilityConfirmationIsRemovedFromTheNextAnalysisRequest()
            throws Exception {
        String incidentId = "INC-GATE-CANCELLED-FACILITY";
        String requestId = "REQ-GATE-AFTER-CANCEL";
        when(idGenerator.nextId()).thenReturn(
                "CFM-GATE-CANCEL-INCIDENT", "CFM-GATE-CANCEL-FACILITY");
        save(incidentId, "REQ-GATE-CANCEL-INCIDENT", incidentFixture());
        save(incidentId, "REQ-GATE-CANCEL-FACILITY", facilityFixture());
        mockMvc.perform(delete("/api/c2guard/v1/incidents/" + incidentId
                        + "/confirmations/FACILITY/CFM-GATE-CANCEL-FACILITY")
                        .cookie(responder(tokenService, incidentId))
                        .header("X-Request-Id", "REQ-GATE-CANCEL"))
                .andExpect(status().isOk());
        stubModelResponse(requestId, incidentId, "ANL-GATE-AFTER-CANCEL");

        mockMvc.perform(post("/api/c2guard/v1/incidents/analyze")
                        .cookie(responder(tokenService, incidentId))
                        .header("X-Request-Id", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(analysisFixture(incidentId)))
                .andExpect(status().isOk());

        ArgumentCaptor<JsonNode> request = ArgumentCaptor.forClass(JsonNode.class);
        verify(modelApiClient).stepIncidentAgent(request.capture(), eq(requestId));
        JsonNode analysis = request.getValue().path("analysis");
        assertTrue(analysis.has("confirmed_incident_substance"));
        assertFalse(analysis.has("confirmed_facility_substance"));
        assertTrue(confirmationStore.findActive(
                incidentId, ConfirmationRole.FACILITY).isEmpty());
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
        ObjectNode response = modelResponse(requestId, incidentId, analysisId);
        JsonNode agent = IncidentAgentTestResponse.withAnalysis(objectMapper, response,
                requestId, incidentId, null);
        when(modelApiClient.stepIncidentAgent(any(JsonNode.class), eq(requestId)))
                .thenReturn(new ModelApiResponse(requestId, agent));
    }

    private ObjectNode modelResponse(String requestId, String incidentId,
                                     String analysisId) throws Exception {
        ObjectNode response = (ObjectNode) objectMapper.readTree(Files.readString(Path.of(
                "src/test/resources/fixtures/model/incident_unconfirmed_response.json")));
        response.put("request_id", requestId);
        response.put("incident_id", incidentId);
        response.put("analysis_id", analysisId);
        return response;
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
