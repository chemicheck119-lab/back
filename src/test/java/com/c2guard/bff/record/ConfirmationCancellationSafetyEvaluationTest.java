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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
class ConfirmationCancellationSafetyEvaluationTest {

    private static final Path SCENARIO_PATH = Path.of(
            "src/test/resources/evaluation/confirmation_cancellation_state_v1.json");
    private static final Path SOURCE_PATH = Path.of(
            "src/test/java/com/c2guard/bff/record/ConfirmationCancellationSafetyEvaluationTest.java");
    private static final Path MIGRATION_PATH = Path.of(
            "src/main/resources/db/migration/V5__add_confirmation_cancellation_audit.sql");
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
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private ConfirmationIdGenerator confirmationIdGenerator;

    @MockBean
    private RecordIdGenerator recordIdGenerator;

    @MockBean
    private ModelApiClient modelApiClient;

    @Test
    void writesAReproducibleCancellationInvalidationReport() throws Exception {
        JsonNode scenario = objectMapper.readTree(Files.readString(SCENARIO_PATH));
        List<Map<String, Object>> checks = new ArrayList<>();
        Throwable failure = null;
        try {
            executeScenario(scenario.path("input"), scenario.path("expected"), checks);
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
        String incidentConfirmationId = text(input, "incident_confirmation_id");
        String facilityConfirmationId = text(input, "facility_confirmation_id");
        String preCancelAnalysisId = text(input, "pre_cancel_analysis_id");
        String postCancelAnalysisId = text(input, "post_cancel_analysis_id");
        String recordId = text(input, "record_id");
        when(confirmationIdGenerator.nextId()).thenReturn(
                incidentConfirmationId, facilityConfirmationId);
        when(recordIdGenerator.nextId()).thenReturn(recordId);

        saveConfirmation(incidentId, "REQ-CANCEL-EVAL-INCIDENT", "INCIDENT",
                "7681-52-9", "차아염소산나트륨");
        saveConfirmation(incidentId, "REQ-CANCEL-EVAL-FACILITY", "FACILITY",
                "7647-01-0", "염산");
        seedAnalysis(incidentId, preCancelAnalysisId, true);

        MvcResult cancelled = cancelConfirmation(incidentId, facilityConfirmationId,
                "REQ-CANCEL-EVAL-CANCEL-1");
        JsonNode cancelledResponse = objectMapper.readTree(
                cancelled.getResponse().getContentAsString());
        addCheck(checks, "cancel_http_status",
                expected.path("cancel_http_status").asInt(),
                cancelled.getResponse().getStatus());
        addCheck(checks, "cancel_response_status",
                expected.path("cancel_response_status").asText(),
                cancelledResponse.path("status").asText());
        addCheck(checks, "cancel_reanalyze_required",
                expected.path("cancel_reanalyze_required").asBoolean(),
                cancelledResponse.path("reanalyzeRequired").asBoolean());

        MvcResult retry = cancelConfirmation(incidentId, facilityConfirmationId,
                "REQ-CANCEL-EVAL-CANCEL-2");
        JsonNode retryResponse = objectMapper.readTree(
                retry.getResponse().getContentAsString());
        addCheck(checks, "cancel_retry_http_status",
                expected.path("cancel_retry_http_status").asInt(),
                retry.getResponse().getStatus());
        addCheck(checks, "cancel_retry_keeps_original_time",
                expected.path("cancel_retry_keeps_original_time").asBoolean(),
                cancelledResponse.path("cancelledAt").equals(
                        retryResponse.path("cancelledAt")));
        addCheck(checks, "active_confirmation_count",
                expected.path("active_confirmation_count").asInt(),
                confirmationStore.findActiveForIncident(incidentId).size());
        addCheck(checks, "active_facility_confirmation_present",
                expected.path("active_facility_confirmation_present").asBoolean(),
                confirmationStore.findActive(
                        incidentId, ConfirmationRole.FACILITY).isPresent());
        addCheck(checks, "cancelled_confirmation_status",
                expected.path("cancelled_confirmation_status").asText(),
                confirmationStore.findById(facilityConfirmationId)
                        .orElseThrow().status().name());
        addCheck(checks, "cancellation_audit_count",
                expected.path("cancellation_audit_count").asInt(),
                cancellationAuditCount(facilityConfirmationId));
        addCheck(checks, "cancellation_audit_request_id",
                expected.path("cancellation_audit_request_id").asText(),
                confirmationStore.findCancellation(facilityConfirmationId)
                        .orElseThrow().cancelledRequestId());

        MvcResult stale = saveRecord(incidentId, "REQ-CANCEL-EVAL-STALE",
                recordRequest(preCancelAnalysisId, List.of(incidentConfirmationId)));
        JsonNode staleResponse = objectMapper.readTree(
                stale.getResponse().getContentAsString());
        addCheck(checks, "stale_record_http_status",
                expected.path("stale_record_http_status").asInt(),
                stale.getResponse().getStatus());
        addCheck(checks, "stale_record_error_code",
                expected.path("stale_record_error_code").asText(),
                staleResponse.path("error").path("code").asText());
        addCheck(checks, "record_count_after_stale_attempt",
                expected.path("record_count_after_stale_attempt").asInt(),
                recordCount(incidentId));

        seedAnalysis(incidentId, postCancelAnalysisId, false);
        IncidentAnalysisSnapshotStore.Snapshot postCancel = analysisStore.find(
                postCancelAnalysisId).orElseThrow();
        addCheck(checks, "post_cancel_incident_binding",
                expected.path("post_cancel_incident_binding").asText(),
                postCancel.incidentConfirmationId());
        addCheck(checks, "post_cancel_facility_binding_is_null",
                expected.path("post_cancel_facility_binding_is_null").asBoolean(),
                postCancel.facilityConfirmationId() == null);
        addCheck(checks, "post_cancel_rule_executed",
                expected.path("post_cancel_rule_executed").asBoolean(),
                postCancel.bffResponse().path("conflictReview")
                        .path("executed").asBoolean());
        addCheck(checks, "post_cancel_risk_display_allowed",
                expected.path("post_cancel_risk_display_allowed").asBoolean(),
                postCancel.bffResponse().path("riskDisplayAllowed").asBoolean());

        MvcResult fresh = saveRecord(incidentId, "REQ-CANCEL-EVAL-FRESH",
                recordRequest(postCancelAnalysisId, List.of(incidentConfirmationId)));
        addCheck(checks, "fresh_record_http_status",
                expected.path("fresh_record_http_status").asInt(),
                fresh.getResponse().getStatus());
        addCheck(checks, "fresh_record_confirmation_reference_count",
                expected.path("fresh_record_confirmation_reference_count").asInt(),
                childCount("response_record_confirmations", recordId));
        addCheck(checks, "record_count_after_fresh_save",
                expected.path("record_count_after_fresh_save").asInt(),
                recordCount(incidentId));
    }

    private void saveConfirmation(String incidentId, String requestId, String role,
                                  String casNumber, String displayName) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("role", role);
        body.put("casNumber", casNumber);
        body.put("displayName", displayName);
        body.put("confirmationBasis", "CONTAINER_LABEL");
        body.put("observedAt", "2026-07-31T14:25:00+09:00");
        MvcResult result = mockMvc.perform(post("/api/c2guard/v1/incidents/"
                        + incidentId + "/confirmations")
                        .cookie(responder(tokenService, incidentId))
                        .header("X-Request-Id", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(body)))
                .andReturn();
        assertEquals(201, result.getResponse().getStatus());
    }

    private MvcResult cancelConfirmation(String incidentId, String confirmationId,
                                         String requestId) throws Exception {
        return mockMvc.perform(delete("/api/c2guard/v1/incidents/" + incidentId
                        + "/confirmations/FACILITY/" + confirmationId)
                        .cookie(responder(tokenService, incidentId))
                        .header("X-Request-Id", requestId))
                .andReturn();
    }

    private void seedAnalysis(String incidentId, String analysisId,
                              boolean completePair) {
        ObjectNode model = objectMapper.createObjectNode().put("analysis_id", analysisId);
        ObjectNode bff = objectMapper.createObjectNode();
        bff.put("analysisId", analysisId);
        bff.put("riskDisplayAllowed", completePair);
        ObjectNode gate = bff.putObject("confirmationGate");
        gate.put("incidentConfirmed", true);
        gate.put("facilityConfirmed", completePair);
        gate.put("allRequiredConfirmed", completePair);
        gate.put("ruleExecutionAllowed", completePair);
        ObjectNode review = bff.putObject("conflictReview");
        review.put("executed", completePair);
        review.put("status", completePair
                ? "SCREENING_COMPLETED" : "NOT_RUN_REQUIRES_TWO_CONFIRMED_CAS");
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
        user.put("messageId", "MSG-CANCEL-EVAL-1");
        user.put("sequence", 1);
        user.put("role", "USER");
        user.put("text", "공개 합성 확인 취소 상태 전이");
        user.put("createdAt", "2026-07-31T14:20:00+09:00");
        user.putNull("analysisId");
        ObjectNode assistant = messages.addObject();
        assistant.put("messageId", "MSG-CANCEL-EVAL-2");
        assistant.put("sequence", 2);
        assistant.put("role", "ASSISTANT");
        assistant.put("text", "확인 취소 뒤 재분석 상태");
        assistant.put("createdAt", "2026-07-31T14:20:02+09:00");
        assistant.put("analysisId", analysisId);
        body.putArray("analysisIds").add(analysisId);
        ArrayNode confirmations = body.putArray("confirmationIds");
        confirmationIds.forEach(confirmations::add);
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

    private int cancellationAuditCount(String confirmationId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM confirmation_cancellations WHERE confirmation_id = ?",
                Integer.class, confirmationId);
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
        long passedCount = checks.stream().filter(row -> Boolean.TRUE.equals(
                row.get("passed"))).count();
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("schema_version",
                "chemicheck119-confirmation-cancellation-evaluation-v1");
        report.put("status", failure == null ? "COMPLETED" : "FAILED");
        report.put("claim_scope", "INTERNAL_REGRESSION_ONLY");
        report.put("field_validated", false);
        report.put("cloud_sql_validated", false);
        report.put("database_runtime", databaseRuntime());
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
        artifacts.put("cancellation_migration", artifact(MIGRATION_PATH));
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

    private String databaseRuntime() {
        try (java.sql.Connection connection = Objects.requireNonNull(
                jdbcTemplate.getDataSource()).getConnection()) {
            return connection.getMetaData().getDatabaseProductName();
        } catch (java.sql.SQLException error) {
            throw new IllegalStateException("database runtime을 확인하지 못했습니다.", error);
        }
    }

    private Map<String, String> artifact(Path path) throws IOException {
        Map<String, String> artifact = new LinkedHashMap<>();
        artifact.put("file_name", path.getFileName().toString());
        artifact.put("sha256", sha256(path));
        return artifact;
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
        String configured = System.getenv(
                "CHEMICHECK119_CONFIRMATION_CANCELLATION_REPORT");
        return configured == null || configured.isBlank()
                ? Path.of("build/reports/evaluation/confirmation-cancellation-state-v1.json")
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
