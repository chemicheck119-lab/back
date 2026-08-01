package com.c2guard.bff.incident;

import com.c2guard.integration.model.ModelApiClient;
import com.c2guard.integration.model.ModelApiErrorKind;
import com.c2guard.integration.model.ModelApiException;
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
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.c2guard.security.BffTestSession.responder;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class IncidentAnalysisControllerTest {

    private static final String PATH = "/api/c2guard/v1/incidents/analyze";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IncidentAnalysisSnapshotStore snapshotStore;

    @Autowired
    private SignedSessionTokenService tokenService;

    @MockBean
    private ModelApiClient modelApiClient;

    @Autowired
    private IncidentAgentMemoryStore agentMemoryStore;

    @Test
    void returnsTheExactClientFixtureAndForwardsTheSameRequestId() throws Exception {
        String requestId = "REQ-EXAMPLE-0001";
        JsonNode model = load("src/test/resources/fixtures/model/incident_unconfirmed_response.json");
        JsonNode expected = load("contracts/examples/bff/incident_awaiting_confirmation_response.json");
        JsonNode agent = IncidentAgentTestResponse.withAnalysis(objectMapper, model,
                requestId, "INC-EXAMPLE-0001", null);
        when(modelApiClient.stepIncidentAgent(any(JsonNode.class), eq(requestId)))
                .thenReturn(new ModelApiResponse(requestId, agent));

        mockMvc.perform(post(PATH)
                        .cookie(responder(tokenService, "INC-EXAMPLE-0001"))
                        .header("X-Request-Id", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Files.readString(Path.of(
                                "contracts/examples/bff/incident_analyze_request.json"))))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", requestId))
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        allOf(containsString("CHEMICHECK119_SESSION="),
                                containsString("HttpOnly"),
                                containsString("Secure"),
                                containsString("SameSite=Lax"))))
                .andExpect(content().json(expected.toString(), true));

        JsonNode snapshot = snapshotStore.find("ANL-EXAMPLE-0001").orElseThrow().modelResponse();
        org.junit.jupiter.api.Assertions.assertEquals(model, snapshot);
    }

    @Test
    void rejectsInvalidClientInputBeforeCallingTheModel() throws Exception {
        String requestId = "REQ-BFF-INVALID-0001";

        mockMvc.perform(post(PATH)
                        .cookie(responder(tokenService, "INC-EXAMPLE-0001"))
                        .header("X-Request-Id", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"   \",\"evidenceTopK\":11}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Request-Id", requestId))
                .andExpect(jsonPath("$.schemaVersion")
                        .value("chemicheck119-dashboard-bff-v1"))
                .andExpect(jsonPath("$.requestId").value(requestId))
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.error.retryable").value(false))
                .andExpect(jsonPath("$.resetAllowed").value(false));

        verifyNoInteractions(modelApiClient);
    }

    @Test
    void mapsModelTimeoutToTheClientContract() throws Exception {
        String requestId = "REQ-BFF-TIMEOUT-0001";
        ModelApiException timeout = new ModelApiException(
                ModelApiErrorKind.TIMEOUT, "MODEL_TIMEOUT", "request timed out",
                true, null, requestId, List.of(), null);
        when(modelApiClient.stepIncidentAgent(any(JsonNode.class), eq(requestId)))
                .thenThrow(timeout);

        mockMvc.perform(post(PATH)
                        .cookie(responder(tokenService, "INC-EXAMPLE-0001"))
                        .header("X-Request-Id", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Files.readString(Path.of(
                                "contracts/examples/bff/incident_analyze_request.json"))))
                .andExpect(status().isGatewayTimeout())
                .andExpect(header().string("X-Request-Id", requestId))
                .andExpect(content().json(Files.readString(Path.of(
                        "contracts/examples/bff/model_timeout_error_response.json")), true));
    }

    @Test
    void rejectsAnUpstreamResponseThatBreaksRequestTracing() throws Exception {
        String requestId = "REQ-BFF-CONTRACT-0001";
        ObjectNode model = (ObjectNode) load(
                "src/test/resources/fixtures/model/incident_unconfirmed_response.json");
        model.put("request_id", "REQ-WRONG");
        JsonNode agent = IncidentAgentTestResponse.withAnalysis(objectMapper, model,
                requestId, "INC-EXAMPLE-0001", null);
        when(modelApiClient.stepIncidentAgent(any(JsonNode.class), eq(requestId)))
                .thenReturn(new ModelApiResponse(requestId, agent));

        mockMvc.perform(post(PATH)
                        .cookie(responder(tokenService, "INC-EXAMPLE-0001"))
                        .header("X-Request-Id", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Files.readString(Path.of(
                                "contracts/examples/bff/incident_analyze_request.json"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.requestId").value(requestId))
                .andExpect(jsonPath("$.error.code").value("MODEL_CONTRACT_VIOLATION"))
                .andExpect(jsonPath("$.resetAllowed").value(false));
    }

    @Test
    void mapsRetryableModelReadinessFailureToServiceUnavailable() throws Exception {
        String requestId = "REQ-BFF-UNAVAILABLE-0001";
        ModelApiException unavailable = new ModelApiException(
                ModelApiErrorKind.UPSTREAM, "MODEL_NOT_READY", "artifact not ready",
                true, 503, requestId, List.of(), null);
        when(modelApiClient.stepIncidentAgent(any(JsonNode.class), eq(requestId)))
                .thenThrow(unavailable);

        mockMvc.perform(post(PATH)
                        .cookie(responder(tokenService, "INC-EXAMPLE-0001"))
                        .header("X-Request-Id", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Files.readString(Path.of(
                                "contracts/examples/bff/incident_analyze_request.json"))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.requestId").value(requestId))
                .andExpect(jsonPath("$.error.code").value("MODEL_SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.error.retryable").value(true))
                .andExpect(jsonPath("$.resetAllowed").value(false));
    }

    @Test
    void sendsStoredMemoryAndUsesTheLastSnapshotWhenThereIsNoNewObservation()
            throws Exception {
        String incidentId = "INC-AGENT-NO-CHANGE";
        String firstRequestId = "REQ-AGENT-FIRST";
        String secondRequestId = "REQ-AGENT-SECOND";
        ObjectNode analysis = (ObjectNode) load(
                "src/test/resources/fixtures/model/incident_unconfirmed_response.json");
        analysis.put("request_id", firstRequestId);
        analysis.put("incident_id", incidentId);
        analysis.put("analysis_id", "ANL-AGENT-NO-CHANGE");
        ObjectNode first = IncidentAgentTestResponse.withAnalysis(objectMapper, analysis,
                firstRequestId, incidentId, null);
        ObjectNode second = IncidentAgentTestResponse.withoutAnalysis(objectMapper,
                secondRequestId, incidentId, first.path("memory"));
        when(modelApiClient.stepIncidentAgent(any(JsonNode.class), eq(firstRequestId)))
                .thenReturn(new ModelApiResponse(firstRequestId, first));
        when(modelApiClient.stepIncidentAgent(any(JsonNode.class), eq(secondRequestId)))
                .thenReturn(new ModelApiResponse(secondRequestId, second));

        ObjectNode request = (ObjectNode) load(
                "contracts/examples/bff/incident_analyze_request.json");
        request.put("incidentId", incidentId);
        String body = objectMapper.writeValueAsString(request);
        mockMvc.perform(post(PATH)
                        .cookie(responder(tokenService, incidentId))
                        .header("X-Request-Id", firstRequestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisId").value("ANL-AGENT-NO-CHANGE"));

        mockMvc.perform(post(PATH)
                        .cookie(responder(tokenService, incidentId))
                        .header("X-Request-Id", secondRequestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value(secondRequestId))
                .andExpect(jsonPath("$.analysisId").value("ANL-AGENT-NO-CHANGE"))
                .andExpect(jsonPath("$.riskDisplayAllowed").value(false));

        ArgumentCaptor<JsonNode> requests = ArgumentCaptor.forClass(JsonNode.class);
        verify(modelApiClient, times(2)).stepIncidentAgent(requests.capture(),
                org.mockito.ArgumentMatchers.anyString());
        assertFalse(requests.getAllValues().get(0).has("memory"));
        org.junit.jupiter.api.Assertions.assertEquals(first.path("memory"),
                requests.getAllValues().get(1).path("memory"));
        org.junit.jupiter.api.Assertions.assertEquals(2,
                agentMemoryStore.find(incidentId).orElseThrow().revision());
    }

    @Test
    void storesFailedSafetyMemoryButDoesNotExposeAnAnalysis() throws Exception {
        String incidentId = "INC-AGENT-SAFETY-FAILURE";
        String requestId = "REQ-AGENT-SAFETY-FAILURE";
        ObjectNode failed = IncidentAgentTestResponse.failed(objectMapper, requestId,
                incidentId, null, "FAILED_SAFETY");
        when(modelApiClient.stepIncidentAgent(any(JsonNode.class), eq(requestId)))
                .thenReturn(new ModelApiResponse(requestId, failed));
        ObjectNode request = (ObjectNode) load(
                "contracts/examples/bff/incident_analyze_request.json");
        request.put("incidentId", incidentId);

        mockMvc.perform(post(PATH)
                        .cookie(responder(tokenService, incidentId))
                        .header("X-Request-Id", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("AGENT_SAFETY_FAILURE"))
                .andExpect(jsonPath("$.error.retryable").value(false))
                .andExpect(jsonPath("$.resetAllowed").value(false));

        org.junit.jupiter.api.Assertions.assertEquals("FAILED_SAFETY",
                agentMemoryStore.find(incidentId).orElseThrow().memory()
                        .path("status").asText());
    }

    private JsonNode load(String path) throws Exception {
        return objectMapper.readTree(Files.readString(Path.of(path)));
    }
}
