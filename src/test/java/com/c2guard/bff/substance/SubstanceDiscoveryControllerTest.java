package com.c2guard.bff.substance;

import com.c2guard.integration.model.ModelApiClient;
import com.c2guard.integration.model.ModelApiResponse;
import com.c2guard.security.SignedSessionTokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static com.c2guard.security.BffTestSession.responder;

@SpringBootTest
@AutoConfigureMockMvc
class SubstanceDiscoveryControllerTest {

    private static final String PATH = "/api/c2guard/v1/substances/discover";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SignedSessionTokenService tokenService;

    @MockBean
    private ModelApiClient modelApiClient;

    @Test
    void connectsTheCurrentClientRequestToTheModelAndReturnsTheBffFixture() throws Exception {
        String requestId = "REQ-BFF-EXAMPLE-0001";
        JsonNode model = load("src/test/resources/fixtures/model/material_discovery_response.json");
        JsonNode expected = load(
                "contracts/examples/bff/material_discovery_candidates_response.json");
        when(modelApiClient.discoverSubstances(any(JsonNode.class), eq(requestId)))
                .thenReturn(new ModelApiResponse(requestId, model));

        mockMvc.perform(post(PATH)
                        .cookie(responder(tokenService, "INC-EXAMPLE-0001"))
                        .header("X-Request-Id", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Files.readString(Path.of(
                                "contracts/examples/bff/material_discovery_request.json"))))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", requestId))
                .andExpect(content().json(expected.toString(), true));

        ArgumentCaptor<JsonNode> request = ArgumentCaptor.forClass(JsonNode.class);
        verify(modelApiClient).discoverSubstances(request.capture(), eq(requestId));
        assertEquals(3, request.getValue().path("top_k").asInt());
        assertEquals(3, request.getValue().path("evidence_top_k").asInt());
    }

    @Test
    void rejectsAnInsufficientQueryBeforeCallingTheModel() throws Exception {
        String requestId = "REQ-BFF-DISCOVERY-INVALID";

        mockMvc.perform(post(PATH)
                        .cookie(responder(tokenService, "INC-EXAMPLE-0001"))
                        .header("X-Request-Id", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"염\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.requestId").value(requestId))
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.resetAllowed").value(false));

        verifyNoInteractions(modelApiClient);
    }

    private JsonNode load(String path) throws Exception {
        return objectMapper.readTree(Files.readString(Path.of(path)));
    }
}
