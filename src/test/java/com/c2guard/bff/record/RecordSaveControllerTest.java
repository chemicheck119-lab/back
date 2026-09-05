package com.c2guard.bff.record;

import com.c2guard.bff.common.BffContractException;
import com.c2guard.bff.confirmation.ConfirmationIdGenerator;
import com.c2guard.bff.confirmation.ConfirmationStore;
import com.c2guard.bff.confirmation.SubstanceConfirmation;
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
import java.util.Map;
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
    private ConfirmationStore confirmationStore;

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
        String incidentConfirmationId = "CNF-RECORD-CREATE-INCIDENT";
        String facilityConfirmationId = "CNF-RECORD-CREATE-FACILITY";
        when(confirmationIdGenerator.nextId()).thenReturn(
                incidentConfirmationId, facilityConfirmationId);
        seedConfirmation(incidentId, "INCIDENT", "7681-52-9",
                "차아염소산나트륨");
        seedConfirmation(incidentId, "FACILITY", "7647-01-0", "염산");
        seedAnalysisWithConflict(incidentId, analysisId);
        when(recordIdGenerator.nextId()).thenReturn("REC-RECORD-CREATE");

        mockMvc.perform(post(path(incidentId))
                        .cookie(responder(tokenService, incidentId))
                        .header("X-Request-Id", "REQ-RECORD-CREATE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestWithConfirmations(analysisId, List.of(
                                incidentConfirmationId, facilityConfirmationId))))
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
        assertEquals(2, count("response_record_confirmations", stored.recordId()));
        assertEquals(2, count("incident_response_actions", stored.recordId()));
        assertEquals(1, count("incident_additional_factors", stored.recordId()));
        assertEquals(1, count("incident_conflict_risks", stored.recordId()));
        assertEquals(2, count("incident_conflict_hazards", stored.recordId()));
        assertEquals(1, count("incident_conflict_gas_products", stored.recordId()));

        Map<String, Object> summary = jdbcTemplate.queryForMap("""
                SELECT facility_name, incident_substance_name,
                       incident_substance_cas, brief_application_status,
                       final_response_outcome
                FROM incident_response_summaries WHERE record_id = ?
                """, stored.recordId());
        assertEquals("울산 화학공장", summary.get("facility_name"));
        assertEquals("차아염소산나트륨", summary.get("incident_substance_name"));
        assertEquals("7681-52-9", summary.get("incident_substance_cas"));
        assertEquals("APPLIED", summary.get("brief_application_status"));
        assertEquals("SPREAD_CONTAINED", summary.get("final_response_outcome"));
        Map<String, Object> risk = jdbcTemplate.queryForMap("""
                SELECT facility_substance_name, facility_substance_cas,
                       risk_level, risk_level_ko
                FROM incident_conflict_risks WHERE record_id = ?
                """, stored.recordId());
        assertEquals("염산", risk.get("facility_substance_name"));
        assertEquals("7647-01-0", risk.get("facility_substance_cas"));
        assertEquals("HIGH", risk.get("risk_level"));
        assertEquals("높음", risk.get("risk_level_ko"));

        ResponseRecordStore recreated = new ResponseRecordStore(jdbcTemplate,
                new TransactionTemplate(transactionManager), objectMapper,
                recordIdGenerator, clock);
        assertEquals(stored, recreated.findById(stored.recordId()).orElseThrow());
    }

    @Test
    void rejectsAConflictAnalysisAfterItsConfirmationWasCorrected() throws Exception {
        String incidentId = "INC-RECORD-STALE-CONFIRMATION";
        String analysisId = "ANL-RECORD-STALE-CONFIRMATION";
        String oldIncidentId = "CNF-RECORD-STALE-INCIDENT-1";
        String facilityId = "CNF-RECORD-STALE-FACILITY";
        String correctedIncidentId = "CNF-RECORD-STALE-INCIDENT-2";
        when(confirmationIdGenerator.nextId()).thenReturn(
                oldIncidentId, facilityId, correctedIncidentId);
        seedConfirmation(incidentId, "INCIDENT", "7681-52-9",
                "차아염소산나트륨");
        seedConfirmation(incidentId, "FACILITY", "7647-01-0", "염산");
        seedAnalysisWithConflict(incidentId, analysisId);
        seedConfirmation(incidentId, "INCIDENT", "7664-93-9", "황산");

        mockMvc.perform(post(path(incidentId))
                        .cookie(responder(tokenService, incidentId))
                        .header("X-Request-Id", "REQ-RECORD-STALE-CONFIRMATION")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestWithConfirmations(analysisId, List.of(
                                correctedIncidentId, facilityId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code")
                        .value("INCIDENT_REFERENCE_CONFLICT"))
                .andExpect(jsonPath("$.resetAllowed").value(false));

        verifyNoInteractions(recordIdGenerator);
    }

    @Test
    void transactionRechecksConfirmationHeadsBeforeInsertingARecord() throws Exception {
        String incidentId = "INC-RECORD-HEAD-LOCK";
        String oldIncidentId = "CNF-RECORD-HEAD-INCIDENT-1";
        String facilityId = "CNF-RECORD-HEAD-FACILITY";
        when(confirmationIdGenerator.nextId()).thenReturn(
                oldIncidentId, facilityId, "CNF-RECORD-HEAD-INCIDENT-2");
        seedConfirmation(incidentId, "INCIDENT", "7681-52-9",
                "차아염소산나트륨");
        seedConfirmation(incidentId, "FACILITY", "7647-01-0", "염산");
        List<SubstanceConfirmation> stale = List.of(
                confirmationStore.findById(oldIncidentId).orElseThrow(),
                confirmationStore.findById(facilityId).orElseThrow());
        seedConfirmation(incidentId, "INCIDENT", "7664-93-9", "황산");
        String analysisId = "ANL-RECORD-HEAD-LOCK";
        seedAnalysis(incidentId, analysisId);
        RecordSaveRequest request = new RecordSaveRequest(
                OffsetDateTime.parse("2026-07-31T14:20:00+09:00"),
                List.of(message("MSG-HEAD-LOCK", 1, analysisId)),
                List.of(analysisId), List.of(oldIncidentId, facilityId), outcome());
        BffUserPrincipal principal = new BffUserPrincipal("responder-head-lock",
                "fire-station-119", Set.of(BffRole.RESPONDER), Set.of(incidentId),
                "SID-HEAD-LOCK", Instant.now().minusSeconds(10),
                Instant.now().plusSeconds(3600));
        when(recordIdGenerator.nextId()).thenReturn("REC-RECORD-HEAD-LOCK");

        BffContractException error = assertThrows(BffContractException.class,
                () -> recordStore.save(incidentId, request,
                        fingerprint.calculate(incidentId, request, principal),
                        "REQ-RECORD-HEAD-LOCK", principal,
                        List.of(analysisStore.find(analysisId).orElseThrow()), stale,
                        java.util.Optional.empty(), java.util.Optional.empty(),
                        java.util.Optional.empty()));

        assertEquals(409, error.getStatus());
        assertEquals("INCIDENT_REFERENCE_CONFLICT", error.getCode());
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

        when(confirmationIdGenerator.nextId()).thenReturn(
                "CNF-RECORD-IDEMPOTENT-LATER");
        seedConfirmation(incidentId, "INCIDENT", "7681-52-9",
                "차아염소산나트륨");
        mockMvc.perform(post(path(incidentId))
                        .cookie(responder(tokenService, incidentId))
                        .header("X-Request-Id", "REQ-RECORD-IDEMPOTENT-3")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestId")
                        .value("REQ-RECORD-IDEMPOTENT-3"))
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
    void rejectsARecordWithoutTheStructuredOutcomeReport() throws Exception {
        String incidentId = "INC-RECORD-NO-OUTCOME";
        String analysisId = "ANL-RECORD-NO-OUTCOME";
        seedAnalysis(incidentId, analysisId);
        ObjectNode body = (ObjectNode) objectMapper.readTree(request(analysisId, null));
        body.remove("outcomeReport");

        mockMvc.perform(post(path(incidentId))
                        .cookie(responder(tokenService, incidentId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));

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
                List.of(analysisId), List.of(), outcome());
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

    private void seedAnalysisWithConflict(String incidentId, String analysisId) {
        ObjectNode bff = objectMapper.createObjectNode();
        ObjectNode review = bff.putObject("conflictReview");
        review.put("executed", true);
        ObjectNode result = review.putObject("result");
        result.put("kind", "ORDINAL_SCREENING_RESULT");
        result.put("incidentCas", "7681-52-9");
        result.put("facilityCas", "7647-01-0");
        result.put("ruleId", "CAMEO-REACTIVE-GROUP-COMPATIBILITY-MATRIX");
        result.put("ruleVersion", "RUNTIME-MANIFEST-1");
        result.put("severity", "HIGH_RISK");
        result.put("riskLevel", "HIGH");
        result.put("riskLevelKo", "높음");
        result.put("briefText", "산성 물질과 접촉하면 독성 염소가스가 발생할 수 있습니다.");
        result.put("expertReviewed", false);
        result.put("humanConfirmationRequired", true);
        result.putArray("hazardCodes").add("C").add("T");
        result.putArray("gasProducts").add("Cl2");
        analysisStore.save(incidentId, analysisId, "REQ-" + analysisId,
                objectMapper.createObjectNode(), bff, objectMapper.createObjectNode(),
                confirmationStore.findActiveForIncident(incidentId));
    }

    private void seedConfirmation(String incidentId, String role, String casNumber,
                                  String displayName)
            throws Exception {
        mockMvc.perform(post("/api/c2guard/v1/incidents/" + incidentId
                        + "/confirmations")
                        .cookie(responder(tokenService, incidentId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "role": "%s",
                                  "casNumber": "%s",
                                  "displayName": "%s",
                                  "confirmationBasis": "CONTAINER_LABEL",
                                  "observedAt": "2026-07-31T14:25:00+09:00"
                                }
                                """.formatted(role, casNumber, displayName)))
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
        return requestWithConfirmations(analysisId, confirmationId == null
                ? List.of() : List.of(confirmationId));
    }

    private String requestWithConfirmations(
            String analysisId, List<String> confirmationIds) {
        String confirmations = confirmationIds.stream()
                .map(value -> "\"" + value + "\"")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
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
                  "confirmationIds": %s,
                  "outcomeReport": {
                    "facilityName": "울산 화학공장",
                    "facilityAddress": "울산광역시 남구 산업로 119",
                    "performedActions": ["ZONE_CONTROL", "LEAK_SOURCE_CONTROL"],
                    "briefApplicationStatus": "APPLIED",
                    "additionalFactors": ["ENCLOSED_SPACE"],
                    "finalResponseOutcome": "SPREAD_CONTAINED"
                  }
                }
                """.formatted(analysisId, analysisId, confirmations);
    }

    private StructuredIncidentOutcome outcome() {
        return new StructuredIncidentOutcome("울산 화학공장",
                "울산광역시 남구 산업로 119",
                List.of(StructuredIncidentOutcome.PerformedAction.ZONE_CONTROL),
                StructuredIncidentOutcome.BriefApplicationStatus.APPLIED,
                List.of(StructuredIncidentOutcome.AdditionalFactor.ENCLOSED_SPACE),
                StructuredIncidentOutcome.FinalResponseOutcome.SPREAD_CONTAINED);
    }
}
