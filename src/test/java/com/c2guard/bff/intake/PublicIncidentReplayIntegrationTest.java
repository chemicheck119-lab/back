package com.c2guard.bff.intake;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "chemicheck119.incident-replay.enabled=true",
        "chemicheck119.incident-replay.public-endpoint-enabled=true",
        "chemicheck119.incident-replay.delay=1ms",
        "chemicheck119.incident-replay.timeout=5s"
})
@AutoConfigureMockMvc
class PublicIncidentReplayIntegrationTest {

    private static final String REPLAY_PATH =
            "/api/c2guard/v1/intake/replay-stream/CONTEST-LIVE-CHEMICAL-001";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void publicFeReceivesOneExplicitlySyntheticIncidentOverSse() throws Exception {
        MvcResult stream = mockMvc.perform(get(REPLAY_PATH)
                        .header("X-Request-Id", "REQ-PUBLIC-REPLAY-001")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(stream))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "REQ-PUBLIC-REPLAY-001"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(containsString("event:incident.accepted")))
                .andExpect(content().string(containsString(
                        "\"sourceType\":\"SYNTHETIC_DISPATCH_REPLAY\"")))
                .andExpect(content().string(containsString(
                        "\"dataClassification\":\"PUBLIC_SYNTHETIC\"")))
                .andExpect(content().string(containsString(
                        "\"containsPersonalInformation\":false")))
                .andExpect(content().string(containsString(
                        "\"reportText\":\"차아염소산나트륨 저장탱크 누출 의심")));
    }

    @Test
    void unknownScenarioDoesNotFallbackToTheContestScenario() throws Exception {
        mockMvc.perform(get("/api/c2guard/v1/intake/replay-stream/UNKNOWN")
                        .header("X-Request-Id", "REQ-PUBLIC-REPLAY-404"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("REPLAY_SCENARIO_NOT_FOUND")));
    }
}
