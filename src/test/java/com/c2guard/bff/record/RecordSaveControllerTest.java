package com.c2guard.bff.record;

import com.c2guard.bff.confirmation.ConfirmationIdGenerator;
import com.c2guard.bff.incident.IncidentAnalysisSnapshotStore;
import com.c2guard.integration.model.ModelApiClient;
import com.c2guard.security.BffRole;
import com.c2guard.security.BffUserPrincipal;
import com.c2guard.security.SignedSessionTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

import static com.c2guard.security.BffTestSession.responder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RecordSaveControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SignedSessionTokenService tokenService;

    @Autowired
    private IncidentAnalysisSnapshotStore analysisStore;

    @Autowired
    private ResponseRecordStore recordStore;

    @Autowired
    private RecordFingerprint fingerprint;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Clock clock;

    @MockBean
    private RecordIdGenerator recordIdGenerator;

    @MockBean
    private ConfirmationIdGenerator confirmationIdGenerator;

    @MockBean
    private ModelApiClient modelApiClient;

    @Test
    void atomicallyStoresAuthoritativeReferencesAndSurvivesStoreRecreation()
            throws Exception {
        String incidentId = "INC-RECORD-CREATE";
        String analysisId = "ANL-RECORD-CREATE";
        String confirmationId = "CNF-RECORD-CREATE";
        seedAnalysis(incidentId, analysisId);
        seedConfirmation(incidentId, confirmationId);
        when(recordIdGenerator.nextId()).thenReturn("REC-RECORD-CREATE");

        mockMvc.perform(post(path(incidentId))
                        .cookie(responder(tokenService, incidentId))
                        .header("X-Request-Id", "REQ-RECORD-CREATE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(analysisId, confirmationId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.schemaVersion")
                        .value("chemicheck119-dashboard-bff-v1"))
                .andExpect(jsonPath("$.requestId").value("REQ-RECORD-CREATE"))
                .andExpect(jsonPath("$.incidentId").value(incidentId))
                .andExpect(jsonPath("$.recordId").value("REC-RECORD-CREATE"))
                .andExpect(jsonPath("$.savedAt").isNotEmpty())
                .andExpect(jsonPath("$.resetAllowed").value(true));

        StoredResponseRecord stored = recordStore.findById("REC-RECORD-CREATE")
                .orElseThrow();
        assertEquals("responder-1", stored.savedByUserId());
        assertEquals("fire-station-119", stored.savedByOrganizationId());
        assertEquals(2, recordStore.messageCount(stored.recordId()));
        assertEquals(1, count("response_record_analyses", stored.recordId()));
        assertEquals(1, count("response_record_confirmations", stored.recordId()));

        ResponseRecordStore recreated = new ResponseRecordStore(jdbcTemplate,
                new TransactionTemplate(transactionManager), objectMapper,
                recordIdGenerator, clock);
        assertEquals(stored, recreated.findById(stored.recordId()).orElseThrow());
    }

    @Test
    void exactRetryReturnsTheExistingRecordWithoutDuplicatingMessages() throws Exception {
        String incidentId = "INC-RECORD-IDEMPOTENT";
        String analysisId = "ANL-RECORD-IDEMPOTENT";
        seedAnalysis(incidentId, analysisId);
        when(recordIdGenerator.nextId()).thenReturn("REC-RECORD-IDEMPOTENT");
        String body = request(analysisId, null);

        mockMvc.perform(post(path(incidentId))
                        .cookie(responder(tokenService, incidentId))
                        .header("X-Request-Id", "REQ-RECORD-IDEMPOTENT-1")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post(path(incidentId))
                        .cookie(responder(tokenService, incidentId))
                        .header("X-Request-Id", "REQ-RECORD-IDEMPOTENT-2")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestId")
                        .value("REQ-RECORD-IDEMPOTENT-2"))
                .andExpect(jsonPath("$.recordId")
                        .value("REC-RECORD-IDEMPOTENT"));

        verify(recordIdGenerator, times(1)).nextId();
        assertEquals(2, recordStore.messageCount("REC-RECORD-IDEMPOTENT"));
    }

    @Test
    void rejectsAnAnalysisOwnedByAnotherIncidentBeforeSaving() throws Exception {
        String analysisId = "ANL-RECORD-CROSS-INCIDENT";
        seedAnalysis("INC-RECORD-OWNER", analysisId);

        mockMvc.perform(post(path("INC-RECORD-ATTACKER"))
                        .cookie(responder(tokenService, "INC-RECORD-ATTACKER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(analysisId, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code")
                        .value("INCIDENT_REFERENCE_CONFLICT"))
                .andExpect(jsonPath("$.resetAllowed").value(false));

        verifyNoInteractions(recordIdGenerator);
    }

    @Test
    void rejectsDuplicateOrOutOfOrderMessageSequence() throws Exception {
        String incidentId = "INC-RECORD-BAD-SEQUENCE";
        String analysisId = "ANL-RECORD-BAD-SEQUENCE";
        seedAnalysis(incidentId, analysisId);
        String body = request(analysisId, null).replace("\"sequence\": 2",
                "\"sequence\": 1");

        mockMvc.perform(post(path(incidentId))
                        .cookie(responder(tokenService, incidentId))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.resetAllowed").value(false));

        verifyNoInteractions(recordIdGenerator);
    }

    @Test
    void rollsBackTheParentWhenAChildInsertFails() {
        String incidentId = "INC-RECORD-ROLLBACK";
        String analysisId = "ANL-RECORD-ROLLBACK";
        seedAnalysis(incidentId, analysisId);
        RecordSaveRequest invalid = new RecordSaveRequest(
                OffsetDateTime.parse("2026-07-31T14:20:00+09:00"),
                List.of(
                        message("MSG-ROLLBACK", 1, analysisId),
                        message("MSG-ROLLBACK", 2, analysisId)),
                List.of(analysisId), List.of());
        BffUserPrincipal principal = new BffUserPrincipal("responder-rollback",
                "fire-station-119", Set.of(BffRole.RESPONDER), Set.of(incidentId),
                "SID-ROLLBACK", Instant.now().minusSeconds(10),
                Instant.now().plusSeconds(3600));
        when(recordIdGenerator.nextId()).thenReturn(
                "REC-ROLLBACK-1", "REC-ROLLBACK-2", "REC-ROLLBACK-3",
                "REC-ROLLBACK-4", "REC-ROLLBACK-5");

        assertThrows(IllegalStateException.class, () -> recordStore.save(
                incidentId, invalid, fingerprint.calculate(incidentId, invalid, principal),
                "REQ-ROLLBACK", principal,
                List.of(analysisStore.find(analysisId).orElseThrow()), List.of(),
                java.util.Optional.empty(), java.util.Optional.empty(),
                java.util.Optional.empty()));

        Integer records = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM response_records
                        WHERE record_id LIKE 'REC-ROLLBACK-%'
                        """, Integer.class);
        assertEquals(0, records);
    }

    private RecordSaveRequest.ConversationMessage message(
            String messageId, int sequence, String analysisId) {
        return new RecordSaveRequest.ConversationMessage(messageId, sequence,
                RecordSaveRequest.MessageRole.ASSISTANT, "현장 확인이 필요합니다.",
                OffsetDateTime.parse("2026-07-31T14:20:02+09:00"), analysisId);
    }

    private void seedAnalysis(String incidentId, String analysisId) {
        ObjectNode source = objectMapper.createObjectNode().put("analysisId", analysisId);
        analysisStore.save(incidentId, analysisId, "REQ-" + analysisId,
                source, source, source);
    }

    private void seedConfirmation(String incidentId, String confirmationId)
            throws Exception {
        when(confirmationIdGenerator.nextId()).thenReturn(confirmationId);
        mockMvc.perform(post("/api/c2guard/v1/incidents/" + incidentId
                        + "/confirmations")
                        .cookie(responder(tokenService, incidentId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "role": "INCIDENT",
                                  "casNumber": "7681-52-9",
                                  "displayName": "차아염소산나트륨",
                                  "confirmationBasis": "CONTAINER_LABEL",
                                  "observedAt": "2026-07-31T14:25:00+09:00"
                                }
                                """))
                .andExpect(status().isCreated());
    }

    private int count(String table, String recordId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE record_id = ?",
                Integer.class, recordId);
        return count == null ? 0 : count;
    }

    private String path(String incidentId) {
        return "/api/c2guard/v1/incidents/" + incidentId + "/record";
    }

    private String request(String analysisId, String confirmationId) {
        String confirmations = confirmationId == null
                ? "[]" : "[\"" + confirmationId + "\"]";
        return """
                {
                  "conversationStartedAt": "2026-07-31T14:20:00+09:00",
                  "messages": [
                    {
                      "messageId": "MSG-0001",
                      "sequence": 1,
                      "role": "USER",
                      "text": "차아염소산나트륨 저장탱크 누출",
                      "createdAt": "2026-07-31T14:20:00+09:00",
                      "analysisId": null
                    },
                    {
                      "messageId": "MSG-0002",
                      "sequence": 2,
                      "role": "ASSISTANT",
                      "text": "현장 확인이 필요합니다.",
                      "createdAt": "2026-07-31T14:20:02+09:00",
                      "analysisId": "%s"
                    }
                  ],
                  "analysisIds": ["%s"],
                  "confirmationIds": %s
                }
                """.formatted(analysisId, analysisId, confirmations);
    }
}
