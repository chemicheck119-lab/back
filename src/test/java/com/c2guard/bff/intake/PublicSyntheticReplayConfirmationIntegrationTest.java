package com.c2guard.bff.intake;

import com.c2guard.bff.confirmation.ConfirmationIdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "chemicheck119.incident-replay.enabled=true",
        "chemicheck119.incident-replay.public-endpoint-enabled=true",
        "chemicheck119.incident-replay.synthetic-confirmation-enabled=true",
        "chemicheck119.incident-replay.synthetic-incident-ttl=30m"
})
@AutoConfigureMockMvc
class PublicSyntheticReplayConfirmationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IncidentReplayCatalog catalog;

    @Autowired
    private SyntheticIncidentReplayRegistry registry;

    @MockBean
    private ConfirmationIdGenerator confirmationIdGenerator;

    @BeforeEach
    void configureIds() {
        when(confirmationIdGenerator.nextId())
                .thenReturn("CFM-SYNTHETIC-INCIDENT", "CFM-SYNTHETIC-FACILITY");
    }

    @Test
    void rejectsAnIncidentThatWasNotIssuedByTheReplayRegistry() throws Exception {
        mockMvc.perform(post(path("INC-NOT-REPLAYED", "INCIDENT")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code")
                        .value("SYNTHETIC_INCIDENT_NOT_REGISTERED"));
    }

    @Test
    void recordsOnlyTheTwoServerFixedSyntheticSubstancesAndIsIdempotent()
            throws Exception {
        IncidentEnvelope envelope = catalog.create(
                IncidentReplayCatalog.CONTEST_SCENARIO_ID, "REQ-SYNTHETIC-REPLAY");
        registry.register(envelope);

        String incidentPath = path(envelope.incidentId(), "INCIDENT");
        mockMvc.perform(post(incidentPath)
                        .header("X-Request-Id", "REQ-SYNTHETIC-INCIDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"casNumber\":\"50-00-0\",\"displayName\":\"임의 물질\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmationId").value("CFM-SYNTHETIC-INCIDENT"))
                .andExpect(jsonPath("$.role").value("INCIDENT"))
                .andExpect(jsonPath("$.casNumber").value("7681-52-9"))
                .andExpect(jsonPath("$.displayName").value("차아염소산나트륨"))
                .andExpect(jsonPath("$.dataClassification").value("PUBLIC_SYNTHETIC"))
                .andExpect(jsonPath("$.confirmationType")
                        .value("SYNTHETIC_DEMO_CONFIRMATION"))
                .andExpect(jsonPath("$.confirmedCount").value(1))
                .andExpect(jsonPath("$.allRequiredConfirmed").value(false))
                .andExpect(jsonPath("$.disclosure").value(
                        SyntheticReplayConfirmationResponse.DISCLOSURE));

        mockMvc.perform(post(incidentPath)
                        .header("X-Request-Id", "REQ-SYNTHETIC-INCIDENT-RETRY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmationId").value("CFM-SYNTHETIC-INCIDENT"))
                .andExpect(jsonPath("$.confirmedCount").value(1));

        mockMvc.perform(post(path(envelope.incidentId(), "FACILITY"))
                        .header("X-Request-Id", "REQ-SYNTHETIC-FACILITY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmationId").value("CFM-SYNTHETIC-FACILITY"))
                .andExpect(jsonPath("$.role").value("FACILITY"))
                .andExpect(jsonPath("$.casNumber").value("7647-01-0"))
                .andExpect(jsonPath("$.displayName").value("염산"))
                .andExpect(jsonPath("$.confirmedCount").value(2))
                .andExpect(jsonPath("$.allRequiredConfirmed").value(true))
                .andExpect(jsonPath("$.reanalyzeRequired").value(true));
    }

    @Test
    void rejectsRolesOutsideTheFixedIncidentAndFacilityPair() throws Exception {
        IncidentEnvelope envelope = catalog.create(
                IncidentReplayCatalog.CONTEST_SCENARIO_ID, "REQ-SYNTHETIC-ROLE");
        registry.register(envelope);

        mockMvc.perform(post(path(envelope.incidentId(), "ARBITRARY")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    private static String path(String incidentId, String role) {
        return "/api/c2guard/v1/intake/replays/" + incidentId
                + "/confirmations/" + role;
    }
}
