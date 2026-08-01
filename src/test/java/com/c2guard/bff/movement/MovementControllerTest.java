package com.c2guard.bff.movement;

import com.c2guard.integration.model.ModelApiClient;
import com.c2guard.security.SignedSessionTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static com.c2guard.security.BffTestSession.responder;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MovementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SignedSessionTokenService tokenService;

    @Autowired
    private IncidentMovementContextStore contextStore;

    @MockBean
    private RouteProvider routeProvider;

    @MockBean
    private ModelApiClient modelApiClient;

    @Test
    void returnsAContractSafeUnavailableStateWhenNoLiveProviderIsReady() throws Exception {
        String incidentId = "INC-MOVE-CONTROLLER-1";
        String requestId = "REQ-MOVE-CONTROLLER-1";
        saveContext(incidentId);
        when(routeProvider.findRoute(any())).thenReturn(new RouteProvider.UnavailableRoute(
                "지도 사업자가 아직 구성되지 않았습니다.", false));

        mockMvc.perform(post(path(incidentId))
                        .cookie(responder(tokenService, incidentId))
                        .header("X-Request-Id", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(1, 37.2065, OffsetDateTime.now(ZoneOffset.UTC))))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", requestId))
                .andExpect(jsonPath("$.schemaVersion")
                        .value("chemicheck119-dashboard-bff-v1"))
                .andExpect(jsonPath("$.requestId").value(requestId))
                .andExpect(jsonPath("$.incidentId").value(incidentId))
                .andExpect(jsonPath("$.clientSequence").value(1))
                .andExpect(jsonPath("$.mapContext.coverageScope")
                        .value("NATIONWIDE_KOREA"))
                .andExpect(jsonPath("$.mapContext.route.status")
                        .value("ROUTE_UNAVAILABLE"))
                .andExpect(jsonPath("$.mapContext.route.geometry").value(nullValue()))
                .andExpect(jsonPath("$.mapContext.route.etaSeconds").value(nullValue()))
                .andExpect(jsonPath("$.mapContext.route.progressRatioIsProbability")
                        .value(false))
                .andExpect(jsonPath("$.mapContext.rendering.geometryFormat")
                        .value("GEOJSON_RFC7946"))
                .andExpect(jsonPath("$.mapContext.hazardOverlayStatus")
                        .value("NOT_COMPUTED_NO_VALIDATED_DISPERSION_MODEL"))
                .andExpect(jsonPath("$.nextRefreshSeconds").value(5))
                .andExpect(jsonPath("$.routeRecalculated").value(false));
    }

    @Test
    void returnsUnauthorizedWithoutAServiceSession() throws Exception {
        mockMvc.perform(post(path("INC-MOVE-NO-SESSION"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(1, 37.2065, OffsetDateTime.now(ZoneOffset.UTC))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"));
    }

    @Test
    void rejectsOutOfRangeCoordinates() throws Exception {
        String incidentId = "INC-MOVE-BAD-COORDINATE";

        mockMvc.perform(post(path(incidentId))
                        .cookie(responder(tokenService, incidentId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(1, 91.0, OffsetDateTime.now(ZoneOffset.UTC))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    void mapsARepeatedSequenceToTheBffConflictContract() throws Exception {
        String incidentId = "INC-MOVE-SEQUENCE";
        saveContext(incidentId);
        when(routeProvider.findRoute(any())).thenReturn(
                new RouteProvider.UnavailableRoute("not configured", false));
        String body = request(7, 37.2065, OffsetDateTime.now(ZoneOffset.UTC));

        mockMvc.perform(post(path(incidentId))
                        .cookie(responder(tokenService, incidentId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
        mockMvc.perform(post(path(incidentId))
                        .cookie(responder(tokenService, incidentId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code")
                        .value("MOVEMENT_SEQUENCE_CONFLICT"))
                .andExpect(jsonPath("$.resetAllowed").value(false));
    }

    private void saveContext(String incidentId) {
        contextStore.save(incidentId,
                new IncidentMovementContextStore.IncidentPosition(
                        37.2181, 126.9417, "예시 사업장", "DISPATCH_SYSTEM",
                        OffsetDateTime.now(ZoneOffset.UTC), false),
                "화성소방서");
    }

    private String path(String incidentId) {
        return "/api/c2guard/v1/incidents/" + incidentId + "/movement";
    }

    private String request(long sequence, double latitude, OffsetDateTime observedAt) {
        return """
                {
                  "responderPosition": {
                    "latitude": %s,
                    "longitude": 126.8311,
                    "observedAt": "%s",
                    "source": "MDT_DEVICE_GPS",
                    "accuracyM": 12.0
                  },
                  "journeyState": "EN_ROUTE",
                  "clientSequence": %d
                }
                """.formatted(latitude, observedAt, sequence);
    }
}
