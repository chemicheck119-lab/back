package com.c2guard.security;

import com.c2guard.integration.model.ModelApiClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BffSecurityIntegrationTest {

    private static final String ANALYZE_PATH = "/api/c2guard/v1/incidents/analyze";
    private static final String DISCOVERY_PATH = "/api/c2guard/v1/substances/discover";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SignedSessionTokenService tokenService;

    @MockBean
    private ModelApiClient modelApiClient;

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/c2guard/v1/incidents/analyze",
            "/api/c2guard/v1/incidents/INC-1/confirmations",
            "/api/c2guard/v1/incidents/INC-1/movement",
            "/api/c2guard/v1/incidents/INC-1/record",
            "/api/c2guard/v1/intake/replays/INC-1/confirmations/INCIDENT",
            "/api/c2guard/v1/substances/discover"
    })
    void returnsAStructured401OnEveryBffPathWithoutAServiceSession(String path)
            throws Exception {
        String requestId = "REQ-SECURITY-401";

        mockMvc.perform(post(path)
                        .header("X-Request-Id", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"염소\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Request-Id", requestId))
                .andExpect(jsonPath("$.schemaVersion")
                        .value("chemicheck119-dashboard-bff-v1"))
                .andExpect(jsonPath("$.requestId").value(requestId))
                .andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"))
                .andExpect(jsonPath("$.error.retryable").value(false))
                .andExpect(jsonPath("$.resetAllowed").value(false));

        verifyNoInteractions(modelApiClient);
    }

    @Test
    void expiresATamperedCookieWithoutReflectingItsValue() throws Exception {
        String token = tokenService.issue("responder-1", "station-1",
                java.util.Set.of(BffRole.RESPONDER), java.util.Set.of("INC-1"));
        jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie(
                "CHEMICHECK119_SESSION", token + "tampered");

        mockMvc.perform(post(DISCOVERY_PATH)
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"염소\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        allOf(containsString("CHEMICHECK119_SESSION="),
                                containsString("Max-Age=0"),
                                containsString("HttpOnly"),
                                containsString("Secure"),
                                containsString("SameSite=Lax"))))
                .andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"));
    }

    @Test
    void returns403ForAnIncidentOutsideTheSignedScope() throws Exception {
        String request = Files.readString(Path.of(
                "contracts/examples/bff/incident_analyze_request.json"))
                .replace("INC-EXAMPLE-0001", "INC-FORBIDDEN-0001");

        mockMvc.perform(post(ANALYZE_PATH)
                        .cookie(BffTestSession.responder(tokenService, "INC-ASSIGNED-0001"))
                        .header("X-Request-Id", "REQ-SECURITY-403")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.requestId").value("REQ-SECURITY-403"))
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.resetAllowed").value(false));

        verifyNoInteractions(modelApiClient);
    }

    @Test
    void appliesIncidentScopeBeforeFuturePathControllers() throws Exception {
        mockMvc.perform(post("/api/c2guard/v1/incidents/INC-FORBIDDEN/confirmations")
                        .cookie(BffTestSession.responder(tokenService, "INC-ASSIGNED"))
                        .header("X-Request-Id", "REQ-PATH-SCOPE-403")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.requestId").value("REQ-PATH-SCOPE-403"))
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
    }

    @Test
    void protectsLegacyApiRoutesDuringTheTransition() throws Exception {
        mockMvc.perform(get("/api/facilities/search").param("keyword", "울산"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"));
    }

    @Test
    void protectsSessionContextAndLogoutWithoutASession() throws Exception {
        mockMvc.perform(get("/api/c2guard/v1/session"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"));
        mockMvc.perform(post("/api/c2guard/v1/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"));
    }

    @Test
    void protectsIncidentReplayUnlessThePublicReplayFlagIsExplicitlyEnabled()
            throws Exception {
        mockMvc.perform(get(
                        "/api/c2guard/v1/intake/replay-stream/CONTEST-LIVE-CHEMICAL-001")
                        .header("X-Request-Id", "REQ-REPLAY-PROTECTED"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.requestId").value("REQ-REPLAY-PROTECTED"))
                .andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"));
    }
}
