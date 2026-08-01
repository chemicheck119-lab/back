package com.c2guard.bff.incident;

import com.c2guard.bff.common.BffContractException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Pattern;

@Component
class IncidentAgentResponseValidator {

    static final String AGENT_SCHEMA = "chemicheck119-incident-agent-v1";
    static final String MEMORY_SCHEMA = "chemicheck119-incident-agent-memory-v1";

    private static final Set<String> STATUSES = Set.of(
            "GOAL_COMPLETED",
            "WAITING_FOR_HUMAN",
            "PARTIAL_MAX_ACTIONS",
            "FAILED_RETRYABLE",
            "FAILED_SAFETY"
    );
    private static final Pattern SHA256 = Pattern.compile("^[0-9a-f]{64}$");

    private final IncidentAgentMemoryChecksum checksum;

    IncidentAgentResponseValidator(ObjectMapper objectMapper) {
        this.checksum = new IncidentAgentMemoryChecksum(objectMapper);
    }

    ValidatedIncidentAgentResponse validate(JsonNode source, String expectedRequestId,
                                            String expectedIncidentId) {
        ObjectNode root = requireObject(source, "root");
        requireTextEquals(root, "schema_version", AGENT_SCHEMA);
        requireTextEquals(root, "request_id", expectedRequestId);
        requireTextEquals(root, "incident_id", expectedIncidentId);
        requireTextEquals(root, "planning_mode", "DETERMINISTIC_POLICY_PLANNER");
        requireTextEquals(root, "memory_mode", "BE_PERSISTED_EXTERNAL_MEMORY");
        requireBooleanEquals(root, "memory_can_trigger_rule", false);
        requireBooleanEquals(root, "autonomous_risk_decision_allowed", false);
        requireBooleanEquals(root, "trace_is_chain_of_thought", false);
        requireBooleanEquals(root, "decision_support_only", true);
        requireTextEquals(root, "final_decision_authority", "현장 지휘관");

        String runId = requireText(root, "run_id");
        String status = requireText(root, "status");
        if (!STATUSES.contains(status)) {
            throw violation("지원하지 않는 agent 실행 상태입니다.");
        }
        boolean retryable = requireBoolean(root, "retryable");
        if (("FAILED_RETRYABLE".equals(status)) != retryable) {
            throw violation("agent 실패 상태와 retryable 값이 일치하지 않습니다.");
        }

        ArrayNode events = requireNonEmptyArray(root, "events");
        requireNonEmptyArray(root, "next_actions");
        ArrayNode pendingInputs = requireArray(root, "pending_inputs");

        ObjectNode memory = requireObject(root.get("memory"), "memory");
        requireTextEquals(memory, "schema_version", MEMORY_SCHEMA);
        requireTextEquals(memory, "incident_id", expectedIncidentId);
        requireTextEquals(memory, "last_run_id", runId);
        requireTextEquals(memory, "status", status);
        requireTextEquals(memory, "memory_trust_scope", "ORCHESTRATION_ONLY");
        requireBooleanEquals(memory, "memory_can_trigger_rule", false);
        requirePositiveInteger(memory, "revision");
        requireSha(memory, "request_state_fingerprint", false);
        requireSha(memory, "runtime_state_fingerprint", false);
        requireSha(memory, "memory_sha256", false);
        requireSha(memory, "parent_memory_sha256", true);
        if (!pendingInputs.equals(requireArray(memory, "pending_inputs"))) {
            throw violation("agent 응답과 memory의 대기 입력이 일치하지 않습니다.");
        }
        if (!requireText(memory, "memory_sha256").equals(checksum.calculate(memory))) {
            throw violation("agent memory checksum이 일치하지 않습니다.");
        }

        JsonNode analysis = root.get("analysis");
        if (analysis != null && !analysis.isNull() && !analysis.isObject()) {
            throw violation("agent analysis는 객체 또는 null이어야 합니다.");
        }
        if (status.startsWith("FAILED") && analysis != null && !analysis.isNull()) {
            throw violation("agent 실패 상태에 분석 결과가 포함될 수 없습니다.");
        }

        return new ValidatedIncidentAgentResponse(
                runId,
                status,
                retryable,
                analysis == null || analysis.isNull() ? null : analysis.deepCopy(),
                memory.deepCopy(),
                events.deepCopy()
        );
    }

    private static ObjectNode requireObject(JsonNode source, String label) {
        if (!(source instanceof ObjectNode object)) {
            throw violation("agent 응답의 " + label + " 객체가 없습니다.");
        }
        return object;
    }

    private static ArrayNode requireArray(JsonNode source, String field) {
        JsonNode value = source.get(field);
        if (!(value instanceof ArrayNode array)) {
            throw violation("agent 응답의 " + field + " 배열이 없습니다.");
        }
        return array;
    }

    private static ArrayNode requireNonEmptyArray(JsonNode source, String field) {
        ArrayNode array = requireArray(source, field);
        if (array.isEmpty()) {
            throw violation("agent 응답의 " + field + " 배열이 비어 있습니다.");
        }
        return array;
    }

    private static String requireText(JsonNode source, String field) {
        JsonNode value = source.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw violation("agent 응답의 " + field + " 문자열이 없습니다.");
        }
        return value.asText();
    }

    private static void requireTextEquals(JsonNode source, String field,
                                          String expected) {
        if (!expected.equals(requireText(source, field))) {
            throw violation("agent 응답의 " + field + " 값이 계약과 다릅니다.");
        }
    }

    private static boolean requireBoolean(JsonNode source, String field) {
        JsonNode value = source.get(field);
        if (value == null || !value.isBoolean()) {
            throw violation("agent 응답의 " + field + " boolean이 없습니다.");
        }
        return value.asBoolean();
    }

    private static void requireBooleanEquals(JsonNode source, String field,
                                             boolean expected) {
        if (requireBoolean(source, field) != expected) {
            throw violation("agent 응답의 " + field + " 값이 안전 계약과 다릅니다.");
        }
    }

    private static void requirePositiveInteger(JsonNode source, String field) {
        JsonNode value = source.get(field);
        if (value == null || !value.isIntegralNumber()
                || !value.canConvertToInt() || value.asInt() < 1) {
            throw violation("agent 응답의 " + field + " 값이 양의 정수가 아닙니다.");
        }
    }

    private static void requireSha(JsonNode source, String field, boolean nullable) {
        JsonNode value = source.get(field);
        if (nullable && value != null && value.isNull()) {
            return;
        }
        if (value == null || !value.isTextual()
                || !SHA256.matcher(value.asText()).matches()) {
            throw violation("agent 응답의 " + field + " SHA-256 값이 올바르지 않습니다.");
        }
    }

    private static BffContractException violation(String message) {
        return new BffContractException(422, "MODEL_CONTRACT_VIOLATION",
                message, false);
    }
}
