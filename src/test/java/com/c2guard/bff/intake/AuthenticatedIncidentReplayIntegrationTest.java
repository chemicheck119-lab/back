package com.c2guard.bff.intake;

import com.c2guard.bff.confirmation.ConfirmationIdGenerator;
import com.c2guard.security.BffTestSession;
import com.c2guard.security.SignedSessionTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "chemicheck119.incident-replay.enabled=true",
        "chemicheck119.incident-replay.public-endpoint-enabled=false",
        "chemicheck119.incident-replay.synthetic-confirmation-enabled=true",
        "chemicheck119.incident-replay.delay=1ms",
        "chemicheck119.incident-replay.timeout=5s"
})
@AutoConfigureMockMvc
class AuthenticatedIncidentReplayIntegrationTest {

    private static final String REPLAY_PATH =
            "/api/c2guard/v1/intake/replay-stream/CONTEST-LIVE-CHEMICAL-001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SignedSessionTokenService tokenService;

    @MockBean
    private ConfirmationIdGenerator confirmationIdGenerator;

    @BeforeEach
    void configureIds() {
        when(confirmationIdGenerator.nextId()).thenReturn("CFM-AUTHENTICATED-DEMO");
    }

    @Test
    void signedResponderCanRunReplayAndSyntheticConfirmationWithoutAnonymousAccess()
            throws Exception {
        mockMvc.perform(get(REPLAY_PATH).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized());

        Cookie session = BffTestSession.responder(tokenService, "*");
        MvcResult stream = mockMvc.perform(get(REPLAY_PATH)
                        .cookie(session)
                        .header("X-Request-Id", "REQ-AUTHENTICATED-REPLAY")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = mockMvc.perform(asyncDispatch(stream))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("event:incident.accepted")))
                .andExpect(content().string(containsString(
                        "\"dataClassification\":\"PUBLIC_SYNTHETIC\"")))
                .andReturn().getResponse().getContentAsString();
        String data = body.lines()
                .filter(line -> line.startsWith("data:"))
                .map(line -> line.substring("data:".length()))
                .findFirst()
                .orElseThrow();
        String incidentId = objectMapper.readTree(data).path("incidentId").asText();

        mockMvc.perform(post("/api/c2guard/v1/intake/replays/" + incidentId
                        + "/confirmations/INCIDENT"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/c2guard/v1/intake/replays/" + incidentId
                        + "/confirmations/INCIDENT")
                        .cookie(session)
                        .header("X-Request-Id", "REQ-AUTHENTICATED-CONFIRM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId").value(incidentId))
                .andExpect(jsonPath("$.confirmationId")
                        .value("CFM-AUTHENTICATED-DEMO"))
                .andExpect(jsonPath("$.confirmationType")
                        .value("SYNTHETIC_DEMO_CONFIRMATION"));
    }
}
