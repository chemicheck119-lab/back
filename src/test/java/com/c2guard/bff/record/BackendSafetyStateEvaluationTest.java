package com.c2guard.bff.record;

import com.c2guard.bff.confirmation.ConfirmationIdGenerator;
import com.c2guard.bff.confirmation.ConfirmationRole;
import com.c2guard.bff.confirmation.ConfirmationStore;
import com.c2guard.bff.incident.IncidentAnalysisSnapshotStore;
import com.c2guard.integration.model.ModelApiClient;
import com.c2guard.security.SignedSessionTokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.c2guard.security.BffTestSession.responder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
class BackendSafetyStateEvaluationTest {

    private static final Path SCENARIO_PATH = Path.of(
            "src/test/resources/evaluation/backend_safety_state_v2.json");
    private static final Path SOURCE_PATH = Path.of(
            "src/test/java/com/c2guard/bff/record/BackendSafetyStateEvaluationTest.java");
    private static final Path MIGRATION_PATH = Path.of(
            "src/main/resources/db/migration/V4__bind_analysis_snapshots_to_confirmations.sql");
    private static final Set<PosixFilePermission> OWNER_READ_WRITE = Set.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SignedSessionTokenService tokenService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ConfirmationStore confirmationStore;

    @Autowired
    private IncidentAnalysisSnapshotStore analysisStore;

    @Autowired
    private ResponseRecordStore recordStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private ConfirmationIdGenerator confirmationIdGenerator;

    @MockBean
    private RecordIdGenerator recordIdGenerator;

    @MockBean
    private ModelApiClient modelApiClient;

    @Test
    void writesAReproducibleCorrectionStaleAndRetryReport() throws Exception {
        JsonNode scenario = objectMapper.readTree(Files.readString(SCENARIO_PATH));
        JsonNode input = scenario.path("input");
        JsonNode expected = scenario.path("expected");
        List<Map<String, Object>> checks = new ArrayList<>();
        Throwable failure = null;

        try {
            executeScenario(input, expected, checks);
        } catch (Exception error) {
            failure = error;
            throw error;
        } catch (AssertionError error) {
            failure = error;
            throw error;
        } finally {
            writeReport(scenario, checks, failure);
        }
    }

    private void executeScenario(JsonNode input, JsonNode expected,
                                 List<Map<String, Object>> checks) throws Exception {
        String incidentId = text(input, "incident_id");
        String oldIncidentId = text(input, "old_incident_confirmation_id");
        String facilityId = text(input, "facility_confirmation_id");
        String newEvidenceIncidentId = text(input,
                "new_evidence_incident_confirmation_id");
        String oldAnalysisId = text(input, "old_analysis_id");
        String freshAnalysisId = text(input, "fresh_analysis_id");
        String expectedRecordId = text(input, "record_id");
        JsonNode initialEvidence = input.path("initial_evidence");
        JsonNode newConflictingEvidence = input.path("new_conflicting_evidence");
        when(confirmationIdGenerator.nextId()).thenReturn(
                oldIncidentId, facilityId, newEvidenceIncidentId);
        when(recordIdGenerator.nextId()).thenReturn(expectedRecordId);

        saveConfirmation(incidentId, "REQ-BACKEND-STATE-INCIDENT-1",
                "INCIDENT", text(initialEvidence, "cas_number"),
                text(initialEvidence, "display_name"),
                text(initialEvidence, "confirmation_basis"),
                text(initialEvidence, "observed_at"));
        saveConfirmation(incidentId, "REQ-BACKEND-STATE-INCIDENT-DUPLICATE",
                "INCIDENT", text(initialEvidence, "cas_number"),
                text(initialEvidence, "display_name"),
                text(initialEvidence, "confirmation_basis"),
                text(initialEvidence, "observed_at"));
        addCheck(checks, "duplicate_confirmation_history_count",
                expected.path("duplicate_confirmation_history_count").asInt(),
                confirmationStore.history(incidentId, ConfirmationRole.INCIDENT).size());

        saveConfirmation(incidentId, "REQ-BACKEND-STATE-FACILITY-1",
                "FACILITY", "7647-01-0", "염산", "CONTAINER_LABEL",
                "2026-07-31T14:25:00+09:00");
        seedAnalysis(incidentId, oldAnalysisId, true);
        JsonNode newEvidenceResponse = saveConfirmation(
                incidentId, "REQ-BACKEND-STATE-NEW-EVIDENCE", "INCIDENT",
                text(newConflictingEvidence, "cas_number"),
                text(newConflictingEvidence, "display_name"),
                text(newConflictingEvidence, "confirmation_basis"),
                text(newConflictingEvidence, "observed_at"));

        addCheck(checks, "confirmation_revision_count_after_new_evidence",
                expected.path("confirmation_revision_count_after_new_evidence").asInt(),
                confirmationStore.history(incidentId, ConfirmationRole.INCIDENT).size());
        addCheck(checks, "old_confirmation_status",
                expected.path("old_confirmation_status").asText(),
                confirmationStore.findById(oldIncidentId).orElseThrow().status().name());
        addCheck(checks, "old_confirmation_superseded_by_new_evidence",
                expected.path("old_confirmation_superseded_by_new_evidence").asBoolean(),
                newEvidenceIncidentId.equals(confirmationStore.findById(oldIncidentId)
                        .orElseThrow().supersededByConfirmationId()));
        addCheck(checks, "active_incident_confirmation_id", newEvidenceIncidentId,
                confirmationStore.findActive(incidentId, ConfirmationRole.INCIDENT)
                        .orElseThrow().confirmationId());
        addCheck(checks, "active_confirmation_status",
                expected.path("active_confirmation_status").asText(),
                confirmationStore.findById(newEvidenceIncidentId).orElseThrow()
                        .status().name());
        addCheck(checks, "new_evidence_confirmation_basis",
                expected.path("new_evidence_confirmation_basis").asText(),
                confirmationStore.findById(newEvidenceIncidentId).orElseThrow()
                        .confirmationBasis().name());
        addCheck(checks, "new_evidence_confirmed_cas",
                expected.path("new_evidence_confirmed_cas").asText(),
                confirmationStore.findById(newEvidenceIncidentId).orElseThrow().casNumber());
        addCheck(checks, "new_evidence_confirmation_revision",
                expected.path("new_evidence_confirmation_revision").asLong(),
                confirmationStore.findById(newEvidenceIncidentId).orElseThrow().revision());
        addCheck(checks, "new_evidence_reanalyze_required",
                expected.path("new_evidence_reanalyze_required").asBoolean(),
                newEvidenceResponse.path("reanalyzeRequired").asBoolean());

        String staleBody = recordRequest(oldAnalysisId,
                List.of(newEvidenceIncidentId, facilityId));
        MvcResult stale = saveRecord(incidentId, "REQ-BACKEND-STATE-STALE", staleBody);
        addCheck(checks, "stale_record_http_status",
                expected.path("stale_record_http_status").asInt(),
                stale.getResponse().getStatus());
        JsonNode staleResponse = objectMapper.readTree(stale.getResponse().getContentAsString());
        addCheck(checks, "stale_record_error_code",
                expected.path("stale_record_error_code").asText(),
                staleResponse.path("error").path("code").asText());
        addCheck(checks, "record_count_after_stale_attempt",
                expected.path("record_count_after_stale_attempt").asInt(),
                recordCount(incidentId));

        seedAnalysis(incidentId, freshAnalysisId, false);
        String freshBody = recordRequest(freshAnalysisId,
                List.of(newEvidenceIncidentId, facilityId));
        MvcResult fresh = saveRecord(incidentId, "REQ-BACKEND-STATE-FRESH", freshBody);
        MvcResult retry = saveRecord(incidentId, "REQ-BACKEND-STATE-RETRY", freshBody);
        JsonNode freshResponse = objectMapper.readTree(fresh.getResponse().getContentAsString());
        JsonNode retryResponse = objectMapper.readTree(retry.getResponse().getContentAsString());

        addCheck(checks, "fresh_record_http_status",
                expected.path("fresh_record_http_status").asInt(),
                fresh.getResponse().getStatus());
        addCheck(checks, "retry_record_http_status",
                expected.path("retry_record_http_status").asInt(),
                retry.getResponse().getStatus());
        addCheck(checks, "fresh_record_id", expectedRecordId,
                freshResponse.path("recordId").asText());
        addCheck(checks, "retry_record_id", expectedRecordId,
                retryResponse.path("recordId").asText());
        addCheck(checks, "record_count_after_exact_retry",
                expected.path("record_count_after_exact_retry").asInt(),
                recordCount(incidentId));
        addCheck(checks, "message_count_after_exact_retry",
                expected.path("message_count_after_exact_retry").asInt(),
                recordStore.messageCount(expectedRecordId));
        addCheck(checks, "analysis_reference_count_after_exact_retry",
                expected.path("analysis_reference_count_after_exact_retry").asInt(),
                childCount("response_record_analyses", expectedRecordId));
        addCheck(checks, "confirmation_reference_count_after_exact_retry",
                expected.path("confirmation_reference_count_after_exact_retry").asInt(),
                childCount("response_record_confirmations", expectedRecordId));

        addCheck(checks, "generated_confirmation_id_count",
                expected.path("generated_confirmation_id_count").asInt(),
                invocationCount(confirmationIdGenerator, "nextId"));
        addCheck(checks, "generated_record_id_count",
                expected.path("generated_record_id_count").asInt(),
                invocationCount(recordIdGenerator, "nextId"));
    }

    private JsonNode saveConfirmation(String incidentId, String requestId, String role,
                                      String casNumber, String displayName,
                                      String confirmationBasis, String observedAt)
            throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("role", role);
        body.put("casNumber", casNumber);
        body.put("displayName", displayName);
        body.put("confirmationBasis", confirmationBasis);
        body.put("observedAt", observedAt);
        MvcResult result = mockMvc.perform(post("/api/c2guard/v1/incidents/"
                        + incidentId + "/confirmations")
                        .cookie(responder(tokenService, incidentId))
                        .header("X-Request-Id", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(body)))
                .andReturn();
        assertEquals(201, result.getResponse().getStatus());
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private void seedAnalysis(String incidentId, String analysisId,
                              boolean includeOldConflict) {
        ObjectNode model = objectMapper.createObjectNode().put("analysis_id", analysisId);
        ObjectNode bff = objectMapper.createObjectNode().put("analysisId", analysisId);
        if (includeOldConflict) {
            ObjectNode review = bff.putObject("conflictReview");
            review.put("executed", true);
            ObjectNode result = review.putObject("result");
            result.put("kind", "ORDINAL_SCREENING_RESULT");
            result.put("incidentCas", "7681-52-9");
            result.put("facilityCas", "7647-01-0");
            result.put("riskLevel", "HIGH");
        }
        analysisStore.save(incidentId, analysisId, "REQ-" + analysisId,
                model, bff, objectMapper.createObjectNode(),
                confirmationStore.findActiveForIncident(incidentId));
    }

    private MvcResult saveRecord(String incidentId, String requestId, String body)
            throws Exception {
        return mockMvc.perform(post("/api/c2guard/v1/incidents/" + incidentId + "/record")
                        .cookie(responder(tokenService, incidentId))
                        .header("X-Request-Id", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
    }

    private String recordRequest(String analysisId, List<String> confirmationIds)
            throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("conversationStartedAt", "2026-07-31T14:20:00+09:00");
        ArrayNode messages = body.putArray("messages");
        ObjectNode user = messages.addObject();
        user.put("messageId", "MSG-BACKEND-STATE-1");
        user.put("sequence", 1);
        user.put("role", "USER");
        user.put("text", "공개 합성 사고 지령");
        user.put("createdAt", "2026-07-31T14:20:00+09:00");
        user.putNull("analysisId");
        ObjectNode assistant = messages.addObject();
        assistant.put("messageId", "MSG-BACKEND-STATE-2");
        assistant.put("sequence", 2);
        assistant.put("role", "ASSISTANT");
        assistant.put("text", "확인된 상태 전이 회귀 결과");
        assistant.put("createdAt", "2026-07-31T14:20:02+09:00");
        assistant.put("analysisId", analysisId);
        body.putArray("analysisIds").add(analysisId);
        ArrayNode confirmationArray = body.putArray("confirmationIds");
        confirmationIds.forEach(confirmationArray::add);
        ObjectNode outcome = body.putObject("outcomeReport");
        outcome.put("facilityName", "공개 합성 평가 시설");
        outcome.put("facilityAddress", "공개 합성 주소");
        outcome.putArray("performedActions").add("ZONE_CONTROL");
        outcome.put("briefApplicationStatus", "REVIEWED_NOT_APPLIED");
        outcome.putArray("additionalFactors");
        outcome.put("finalResponseOutcome", "MONITORING_CONTINUES");
        return objectMapper.writeValueAsString(body);
    }

    private int recordCount(String incidentId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM response_records WHERE incident_id = ?",
                Integer.class, incidentId);
        return count == null ? 0 : count;
    }

    private int childCount(String table, String recordId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE record_id = ?",
                Integer.class, recordId);
        return count == null ? 0 : count;
    }

    private void addCheck(List<Map<String, Object>> checks, String name,
                          Object expected, Object actual) {
        Map<String, Object> check = new LinkedHashMap<>();
        check.put("name", name);
        check.put("expected", expected);
        check.put("actual", actual);
        check.put("passed", Objects.equals(expected, actual));
        checks.add(check);
        assertEquals(expected, actual, name);
    }

    private void writeReport(JsonNode scenario, List<Map<String, Object>> checks,
                             Throwable failure) throws IOException {
        Path reportPath = reportPath();
        Files.createDirectories(reportPath.getParent());
        Map<String, Object> report = new LinkedHashMap<>();
        long passedCount = checks.stream().filter(row -> Boolean.TRUE.equals(
                row.get("passed"))).count();
        report.put("schema_version", "chemicheck119-backend-safety-evaluation-v2");
        report.put("status", failure == null ? "COMPLETED" : "FAILED");
        report.put("claim_scope", "INTERNAL_REGRESSION_ONLY");
        report.put("field_validated", false);
        report.put("cloud_sql_validated", false);
        report.put("concurrency_validated", false);
        report.put("database_runtime", "H2_POSTGRESQL_COMPATIBILITY_MODE");
        report.put("case_id", scenario.path("case_id").asText());
        report.put("check_count", checks.size());
        report.put("passed_check_count", passedCount);
        report.put("failed_check_count", checks.size() - passedCount);
        report.put("checks", checks);
        report.put("failure_type", failure == null ? null
                : failure.getClass().getSimpleName());
        Map<String, Object> artifacts = new LinkedHashMap<>();
        artifacts.put("scenario", artifact(SCENARIO_PATH));
        artifacts.put("evaluator_source", artifact(SOURCE_PATH));
        artifacts.put("confirmation_binding_migration", artifact(MIGRATION_PATH));
        report.put("artifacts", artifacts);
        report.put("limitations", objectMapper.convertValue(
                scenario.path("limitations"), List.class));
        Files.writeString(reportPath,
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(report)
                        + System.lineSeparator(), StandardCharsets.UTF_8);
        try {
            Files.setPosixFilePermissions(reportPath, OWNER_READ_WRITE);
        } catch (UnsupportedOperationException ignored) {
            // Windows test runners do not expose POSIX permissions.
        }
    }

    private Map<String, String> artifact(Path path) throws IOException {
        Map<String, String> artifact = new LinkedHashMap<>();
        artifact.put("file_name", path.getFileName().toString());
        artifact.put("sha256", sha256(path));
        return artifact;
    }

    private int invocationCount(Object mock, String methodName) {
        return (int) mockingDetails(mock).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals(methodName))
                .count();
    }

    private String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(path)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", impossible);
        }
    }

    private Path reportPath() {
        String configured = System.getenv("CHEMICHECK119_BACKEND_SAFETY_REPORT");
        return configured == null || configured.isBlank()
                ? Path.of("build/reports/evaluation/backend-safety-state-v2.json")
                : Path.of(configured);
    }

    private String text(JsonNode node, String field) {
        String value = node.path(field).asText();
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + "가 비어 있습니다.");
        }
        return value;
    }
}
