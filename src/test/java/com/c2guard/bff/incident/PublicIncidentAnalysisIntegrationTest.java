package com.c2guard.bff.incident;

import com.c2guard.integration.model.ModelApiClient;
import com.c2guard.integration.model.ModelApiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties =
        "chemicheck119.bff-security.public-analysis-enabled=true")
@AutoConfigureMockMvc
class PublicIncidentAnalysisIntegrationTest {

    private static final String ANALYZE_PATH = "/api/c2guard/v1/incidents/analyze";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ModelApiClient modelApiClient;

    @Test
    void publicFeCanAnalyzeWithoutASessionWhileOperationalApisStayProtected()
            throws Exception {
        String requestId = "REQ-PUBLIC-ANALYSIS-001";
        JsonNode model = objectMapper.readTree(Files.readString(Path.of(
                "src/test/resources/fixtures/model/incident_unconfirmed_response.json")));
        ((com.fasterxml.jackson.databind.node.ObjectNode) model)
                .put("request_id", requestId);
        ((com.fasterxml.jackson.databind.node.ObjectNode) model.path("model_outputs"))
                .putNull("facility_history_candidates");
        JsonNode agent = IncidentAgentTestResponse.withAnalysis(objectMapper, model,
                requestId, "INC-EXAMPLE-0001", null);
        when(modelApiClient.stepIncidentAgent(any(JsonNode.class), eq(requestId)))
                .thenReturn(new ModelApiResponse(requestId, agent));

        mockMvc.perform(post(ANALYZE_PATH)
                        .header("X-Request-Id", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Files.readString(Path.of(
                                "contracts/examples/bff/incident_analyze_request.json"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value(requestId))
                .andExpect(jsonPath("$.facilityHistory.status").value("NOT_QUERIED"))
                .andExpect(jsonPath("$.facilityHistory.candidates").isEmpty());

        mockMvc.perform(get("/api/c2guard/v1/session"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"));
        mockMvc.perform(post("/api/c2guard/v1/incidents/INC-EXAMPLE-0001/confirmations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"));
    }
}
