package com.c2guard.bff.confirmation;

import com.c2guard.integration.model.ModelApiClient;
import com.c2guard.security.SignedSessionTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static com.c2guard.security.BffTestSession.responder;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ConfirmationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SignedSessionTokenService tokenService;

    @Autowired
    private ConfirmationStore store;

    @MockBean
    private ConfirmationIdGenerator idGenerator;

    @MockBean
    private ModelApiClient modelApiClient;

    @Test
    void storesTheAuthenticatedUserAndReturnsTheClientContract() throws Exception {
        String incidentId = "INC-CONFIRM-CREATE";
        when(idGenerator.nextId()).thenReturn("CNF-CONFIRM-CREATE-1");

        mockMvc.perform(post(path(incidentId))
                        .cookie(responder(tokenService, incidentId))
                        .header("X-Request-Id", "REQ-CONFIRM-CREATE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixture()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.schemaVersion")
                        .value("chemicheck119-dashboard-bff-v1"))
                .andExpect(jsonPath("$.requestId").value("REQ-CONFIRM-CREATE"))
                .andExpect(jsonPath("$.incidentId").value(incidentId))
                .andExpect(jsonPath("$.confirmationId").value("CNF-CONFIRM-CREATE-1"))
                .andExpect(jsonPath("$.role").value("INCIDENT"))
                .andExpect(jsonPath("$.casNumber").value("7681-52-9"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.reanalyzeRequired").value(true));

        SubstanceConfirmation stored = store.findById("CNF-CONFIRM-CREATE-1")
                .orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("responder-1",
                stored.confirmedByUserId());
        org.junit.jupiter.api.Assertions.assertEquals("fire-station-119",
                stored.confirmedByOrganizationId());
        org.junit.jupiter.api.Assertions.assertEquals("REQ-CONFIRM-CREATE",
                stored.createdRequestId());
    }

    @Test
    void exactRetryReturnsTheSameAuthorityRecordWithTheCurrentRequestId() throws Exception {
        String incidentId = "INC-CONFIRM-IDEMPOTENT";
        when(idGenerator.nextId()).thenReturn("CNF-CONFIRM-IDEMPOTENT-1");

        mockMvc.perform(post(path(incidentId))
                        .cookie(responder(tokenService, incidentId))
                        .header("X-Request-Id", "REQ-IDEMPOTENT-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixture()))
                .andExpect(status().isCreated());
        mockMvc.perform(post(path(incidentId))
                        .cookie(responder(tokenService, incidentId))
                        .header("X-Request-Id", "REQ-IDEMPOTENT-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixture()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestId").value("REQ-IDEMPOTENT-2"))
                .andExpect(jsonPath("$.confirmationId")
                        .value("CNF-CONFIRM-IDEMPOTENT-1"));

        verify(idGenerator, times(1)).nextId();
    }

    @Test
    void rejectsAnInvalidCasCheckDigitBeforeSaving() throws Exception {
        String incidentId = "INC-CONFIRM-BAD-CAS";

        mockMvc.perform(post(path(incidentId))
                        .cookie(responder(tokenService, incidentId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixture().replace("7681-52-9", "7681-52-8")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        verifyNoInteractions(idGenerator);
        verifyNoInteractions(modelApiClient);
    }

    @Test
    void rejectsAnObservedTimeBeyondTheAllowedClockSkew() throws Exception {
        String incidentId = "INC-CONFIRM-FUTURE";
        String future = OffsetDateTime.now(ZoneOffset.UTC).plusHours(1).toString();
        String request = fixture().replace("2026-07-31T14:25:00+09:00", future);

        mockMvc.perform(post(path(incidentId))
                        .cookie(responder(tokenService, incidentId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

        verifyNoInteractions(idGenerator);
    }

    @Test
    void cancelsOnlyTheNamedActiveConfirmationAndStoresTheActor() throws Exception {
        String incidentId = "INC-CONFIRM-CANCEL";
        when(idGenerator.nextId()).thenReturn("CNF-CONFIRM-CANCEL-1");
        create(incidentId, "REQ-CANCEL-CREATE");

        mockMvc.perform(delete(path(incidentId)
                        + "/INCIDENT/CNF-CONFIRM-CANCEL-1")
                        .cookie(responder(tokenService, incidentId))
                        .header("X-Request-Id", "REQ-CANCEL-DELETE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemaVersion")
                        .value("chemicheck119-dashboard-bff-v1"))
                .andExpect(jsonPath("$.requestId").value("REQ-CANCEL-DELETE"))
                .andExpect(jsonPath("$.incidentId").value(incidentId))
                .andExpect(jsonPath("$.confirmationId")
                        .value("CNF-CONFIRM-CANCEL-1"))
                .andExpect(jsonPath("$.role").value("INCIDENT"))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledAt").isNotEmpty())
                .andExpect(jsonPath("$.reanalyzeRequired").value(true));

        assertTrue(store.findActive(incidentId, ConfirmationRole.INCIDENT).isEmpty());
        ConfirmationCancellation audit = store.findCancellation(
                "CNF-CONFIRM-CANCEL-1").orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("responder-1",
                audit.cancelledByUserId());
        org.junit.jupiter.api.Assertions.assertEquals("fire-station-119",
                audit.cancelledByOrganizationId());
        org.junit.jupiter.api.Assertions.assertEquals("REQ-CANCEL-DELETE",
                audit.cancelledRequestId());
    }

    @Test
    void exactCancellationRetryReturnsTheOriginalAuditTime() throws Exception {
        String incidentId = "INC-CONFIRM-CANCEL-RETRY";
        when(idGenerator.nextId()).thenReturn("CNF-CONFIRM-CANCEL-RETRY-1");
        create(incidentId, "REQ-CANCEL-RETRY-CREATE");

        String cancelPath = path(incidentId)
                + "/INCIDENT/CNF-CONFIRM-CANCEL-RETRY-1";
        String first = mockMvc.perform(delete(cancelPath)
                        .cookie(responder(tokenService, incidentId))
                        .header("X-Request-Id", "REQ-CANCEL-RETRY-1"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String repeated = mockMvc.perform(delete(cancelPath)
                        .cookie(responder(tokenService, incidentId))
                        .header("X-Request-Id", "REQ-CANCEL-RETRY-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("REQ-CANCEL-RETRY-2"))
                .andReturn().getResponse().getContentAsString();

        com.fasterxml.jackson.databind.JsonNode firstJson = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(first);
        com.fasterxml.jackson.databind.JsonNode repeatedJson = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(repeated);
        org.junit.jupiter.api.Assertions.assertEquals(firstJson.path("cancelledAt"),
                repeatedJson.path("cancelledAt"));
        org.junit.jupiter.api.Assertions.assertEquals("REQ-CANCEL-RETRY-1",
                store.findCancellation("CNF-CONFIRM-CANCEL-RETRY-1")
                        .orElseThrow().cancelledRequestId());
    }

    @Test
    void rejectsCancellationOfASupersededConfirmation() throws Exception {
        String incidentId = "INC-CONFIRM-CANCEL-STALE";
        when(idGenerator.nextId()).thenReturn(
                "CNF-CONFIRM-CANCEL-STALE-1", "CNF-CONFIRM-CANCEL-STALE-2");
        create(incidentId, "REQ-CANCEL-STALE-CREATE-1");
        mockMvc.perform(post(path(incidentId))
                        .cookie(responder(tokenService, incidentId))
                        .header("X-Request-Id", "REQ-CANCEL-STALE-CREATE-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixture().replace("7681-52-9", "7647-01-0")))
                .andExpect(status().isCreated());

        mockMvc.perform(delete(path(incidentId)
                        + "/INCIDENT/CNF-CONFIRM-CANCEL-STALE-1")
                        .cookie(responder(tokenService, incidentId))
                        .header("X-Request-Id", "REQ-CANCEL-STALE-DELETE"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code")
                        .value("INCIDENT_REFERENCE_CONFLICT"))
                .andExpect(jsonPath("$.error.retryable").value(true));

        org.junit.jupiter.api.Assertions.assertEquals("CNF-CONFIRM-CANCEL-STALE-2",
                store.findActive(incidentId, ConfirmationRole.INCIDENT)
                        .orElseThrow().confirmationId());
    }

    private void create(String incidentId, String requestId) throws Exception {
        mockMvc.perform(post(path(incidentId))
                        .cookie(responder(tokenService, incidentId))
                        .header("X-Request-Id", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixture()))
                .andExpect(status().isCreated());
    }

    private String path(String incidentId) {
        return "/api/c2guard/v1/incidents/" + incidentId + "/confirmations";
    }

    private String fixture() throws Exception {
        return Files.readString(Path.of("contracts/examples/bff/confirmation_request.json"));
    }
}
