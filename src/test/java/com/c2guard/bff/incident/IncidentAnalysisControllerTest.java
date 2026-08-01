package com.c2guard.bff.incident;

import com.c2guard.integration.model.ModelApiClient;
import com.c2guard.integration.model.ModelApiErrorKind;
import com.c2guard.integration.model.ModelApiException;
import com.c2guard.integration.model.ModelApiResponse;
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
import java.util.List;

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
class IncidentAnalysisControllerTest {

    private static final String PATH = "/api/c2guard/v1/incidents/analyze";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IncidentAnalysisSnapshotStore snapshotStore;

    @MockBean
    private ModelApiClient modelApiClient;

    @Test
    void returnsTheExactClientFixtureAndForwardsTheSameRequestId() throws Exception {
        String requestId = "REQ-EXAMPLE-0001";
        JsonNode model = load("src/test/resources/fixtures/model/incident_unconfirmed_response.json");
        JsonNode expected = load("contracts/examples/bff/incident_awaiting_confirmation_response.json");
        when(modelApiClient.analyzeIncident(any(JsonNode.class), eq(requestId)))
                .thenReturn(new ModelApiResponse(requestId, model));

        mockMvc.perform(post(PATH)
                        .header("X-Request-Id", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Files.readString(Path.of(
                                "contracts/examples/bff/incident_analyze_request.json"))))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", requestId))
                .andExpect(content().json(expected.toString(), true));

        JsonNode snapshot = snapshotStore.find("ANL-EXAMPLE-0001").orElseThrow().modelResponse();
        org.junit.jupiter.api.Assertions.assertEquals(model, snapshot);
    }

    @Test
    void rejectsInvalidClientInputBeforeCallingTheModel() throws Exception {
        String requestId = "REQ-BFF-INVALID-0001";

        mockMvc.perform(post(PATH)
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
        when(modelApiClient.analyzeIncident(any(JsonNode.class), eq(requestId)))
                .thenThrow(timeout);

        mockMvc.perform(post(PATH)
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
        when(modelApiClient.analyzeIncident(any(JsonNode.class), eq(requestId)))
                .thenReturn(new ModelApiResponse(requestId, model));

        mockMvc.perform(post(PATH)
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
        when(modelApiClient.analyzeIncident(any(JsonNode.class), eq(requestId)))
                .thenThrow(unavailable);

        mockMvc.perform(post(PATH)
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

    private JsonNode load(String path) throws Exception {
        return objectMapper.readTree(Files.readString(Path.of(path)));
    }
}
