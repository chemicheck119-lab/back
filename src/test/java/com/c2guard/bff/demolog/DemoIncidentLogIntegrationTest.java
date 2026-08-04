package com.c2guard.bff.demolog;

import com.c2guard.security.BffRole;
import com.c2guard.security.SignedSessionTokenService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "chemicheck119.demo-logs.enabled=true",
        "chemicheck119.demo-logs.records-per-station=15"
})
@AutoConfigureMockMvc
class DemoIncidentLogIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SignedSessionTokenService tokenService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void stationScopedEndpointReturnsOnlyClearlyMarkedSyntheticLogs() throws Exception {
        mockMvc.perform(get("/api/c2guard/v1/demo/incident-logs"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/c2guard/v1/demo/incident-logs")
                        .param("offset", "0")
                        .param("limit", "15")
                        .cookie(stationSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemaVersion")
                        .value("chemicheck119-synthetic-demo-logs-v1"))
                .andExpect(jsonPath("$.dataClassification").value("PUBLIC_SYNTHETIC"))
                .andExpect(jsonPath("$.operationalRecord").value(false))
                .andExpect(jsonPath("$.station.stationId").value("nfa-0985"))
                .andExpect(jsonPath("$.station.stationDisplayName").value("서울 강남소방서"))
                .andExpect(jsonPath("$.station.region").value("서울"))
                .andExpect(jsonPath("$.totalElements").value(15))
                .andExpect(jsonPath("$.logs", hasSize(15)))
                .andExpect(jsonPath("$.logs[*].dataClassification",
                        everyItem(is("PUBLIC_SYNTHETIC"))))
                .andExpect(jsonPath("$.logs[*].operationalRecord",
                        everyItem(is(false))))
                .andExpect(jsonPath("$.logs[*].riskLevel", hasItem("HIGH")))
                .andExpect(jsonPath("$.logs[*].riskLevel", hasItem("MEDIUM")))
                .andExpect(jsonPath("$.logs[*].riskLevel", hasItem("LOW")));
    }

    @Test
    void coverageIncludesAllSeventeenRegionsAndEveryCatalogStation() throws Exception {
        mockMvc.perform(get("/api/c2guard/v1/demo/incident-logs/coverage")
                        .cookie(stationSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.regionCount").value(17))
                .andExpect(jsonPath("$.stationCount").value(215))
                .andExpect(jsonPath("$.scenarioCount").value(15))
                .andExpect(jsonPath("$.totalLogCount").value(3225))
                .andExpect(jsonPath("$.regions", hasSize(17)))
                .andExpect(jsonPath("$.regions[*].stationCount",
                        everyItem(org.hamcrest.Matchers.greaterThan(0))))
                .andExpect(jsonPath("$.regions[*].logCount",
                        everyItem(org.hamcrest.Matchers.greaterThan(0))));

        Integer incompleteStations = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM (
                    SELECT station_id FROM synthetic_demo_incident_logs
                    GROUP BY station_id HAVING COUNT(*) <> 15
                ) incomplete
                """, Integer.class);
        Integer syntheticCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM synthetic_demo_incident_logs
                WHERE data_classification = 'PUBLIC_SYNTHETIC'
                  AND operational_record = FALSE
                """, Integer.class);
        Integer distinctActions = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT performed_action)
                FROM synthetic_demo_incident_logs
                """, Integer.class);
        Integer distinctOutcomes = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT final_response_outcome)
                FROM synthetic_demo_incident_logs
                """, Integer.class);

        assertEquals(0, incompleteStations);
        assertEquals(3225, syntheticCount);
        assertEquals(8, distinctActions);
        assertEquals(6, distinctOutcomes);
    }

    private Cookie stationSession() {
        String token = tokenService.issue(
                "demo-responder", "nfa-0985", "서울 강남소방서",
                Set.of(BffRole.RESPONDER), Set.of("*"));
        return new Cookie("CHEMICHECK119_SESSION", token);
    }
}
