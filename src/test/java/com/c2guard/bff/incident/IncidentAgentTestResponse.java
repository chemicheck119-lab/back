package com.c2guard.bff.incident;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class IncidentAgentTestResponse {

    private static final String FINGERPRINT_A = "a".repeat(64);
    private static final String FINGERPRINT_B = "b".repeat(64);

    private IncidentAgentTestResponse() {
    }

    public static ObjectNode withAnalysis(ObjectMapper objectMapper, JsonNode analysis,
                                          String requestId, String incidentId,
                                          JsonNode previousMemory) {
        String state = analysis.path("state").asText();
        String status = "SCREENING_COMPLETED".equals(state)
                ? "GOAL_COMPLETED" : "WAITING_FOR_HUMAN";
        ArrayNode pending = objectMapper.createArrayNode();
        switch (state) {
            case "AWAITING_SUBSTANCE_CONFIRMATION" -> {
                pending.add("FACILITY_SUBSTANCE_CONFIRMATION");
                pending.add("INCIDENT_SUBSTANCE_CONFIRMATION");
            }
            case "AWAITING_INCIDENT_CONFIRMATION" ->
                    pending.add("INCIDENT_SUBSTANCE_CONFIRMATION");
            case "AWAITING_FACILITY_CONFIRMATION" ->
                    pending.add("FACILITY_SUBSTANCE_CONFIRMATION");
            case "VERIFY_REQUIRED", "UNCLASSIFIED", "CAMEO_GROUP_SCREENING_ONLY" ->
                    pending.add("OFFICIAL_EVIDENCE_REVIEW");
            default -> {
            }
        }
        return response(objectMapper, analysis, requestId, incidentId,
                previousMemory, status, false, pending);
    }

    public static ObjectNode withoutAnalysis(ObjectMapper objectMapper, String requestId,
                                             String incidentId, JsonNode previousMemory) {
        ArrayNode pending = previousMemory == null
                ? objectMapper.createArrayNode()
                : (ArrayNode) previousMemory.path("pending_inputs").deepCopy();
        return response(objectMapper, null, requestId, incidentId,
                previousMemory, "WAITING_FOR_HUMAN", false, pending);
    }

    public static ObjectNode failed(ObjectMapper objectMapper, String requestId,
                                    String incidentId, JsonNode previousMemory,
                                    String status) {
        return response(objectMapper, null, requestId, incidentId, previousMemory,
                status, "FAILED_RETRYABLE".equals(status),
                objectMapper.createArrayNode());
    }

    private static ObjectNode response(ObjectMapper objectMapper, JsonNode analysis,
                                       String requestId, String incidentId,
                                       JsonNode previousMemory, String status,
                                       boolean retryable, ArrayNode pending) {
        String runId = "AGR-" + requestId;
        int previousRevision = previousMemory == null
                ? 0 : previousMemory.path("revision").asInt();
        String parent = previousMemory == null
                ? null : previousMemory.path("memory_sha256").asText();

        ObjectNode memory = objectMapper.createObjectNode();
        memory.put("schema_version", IncidentAgentResponseValidator.MEMORY_SCHEMA);
        memory.put("incident_id", incidentId);
        memory.put("revision", previousRevision + 1);
        memory.put("request_state_fingerprint", FINGERPRINT_A);
        memory.put("runtime_state_fingerprint", FINGERPRINT_B);
        memory.put("status", status);
        memory.set("pending_inputs", pending.deepCopy());
        if (analysis == null) {
            putPreviousOrNull(memory, "last_analysis_state", previousMemory);
            putPreviousOrNull(memory, "last_analysis_id", previousMemory);
        } else {
            memory.put("last_analysis_state", analysis.path("state").asText());
            memory.put("last_analysis_id", analysis.path("analysis_id").asText());
        }
        memory.put("last_run_id", runId);
        memory.putArray("history");
        if (parent == null) {
            memory.putNull("parent_memory_sha256");
        } else {
            memory.put("parent_memory_sha256", parent);
        }
        memory.put("memory_sha256", "0".repeat(64));
        memory.put("memory_trust_scope", "ORCHESTRATION_ONLY");
        memory.put("memory_can_trigger_rule", false);
        memory.put("memory_sha256",
                new IncidentAgentMemoryChecksum(objectMapper).calculate(memory));

        ObjectNode root = objectMapper.createObjectNode();
        root.put("schema_version", IncidentAgentResponseValidator.AGENT_SCHEMA);
        root.put("run_id", runId);
        root.put("request_id", requestId);
        root.put("incident_id", incidentId);
        root.put("status", status);
        root.put("objective", "공식근거 기반 화학사고 충돌 검토 준비");
        root.put("selected_tool_count", analysis == null ? 0 : 1);
        ObjectNode event = root.putArray("events").addObject();
        event.put("sequence", 1);
        event.put("phase", "PLAN");
        event.put("status", analysis == null ? "WAITING" : "COMPLETED");
        event.put("decision_code", analysis == null
                ? "NO_NEW_OBSERVATION" : "CURRENT_REQUEST_REQUIRES_FRESH_ANALYSIS");
        event.put("summary", "계약 테스트용 구조화 agent 이벤트입니다.");
        event.put("occurred_at", "2026-08-01T08:00:00+00:00");
        root.set("memory", memory);
        if (analysis == null) {
            root.putNull("analysis");
        } else {
            root.set("analysis", analysis.deepCopy());
        }
        root.set("pending_inputs", pending.deepCopy());
        root.putArray("next_actions").add("현장 상태를 확인하세요.");
        root.put("retryable", retryable);
        ArrayNode tools = root.putArray("tool_registry");
        tools.add("RUN_INCIDENT_ANALYSIS");
        tools.add("REQUEST_INCIDENT_CONFIRMATION");
        tools.add("REQUEST_FACILITY_CONFIRMATION");
        tools.add("VERIFY_SAFETY_CONTRACT");
        tools.add("REQUEST_OFFICIAL_EVIDENCE_REVIEW");
        tools.add("PRESENT_DECISION_SUPPORT");
        root.put("planning_mode", "DETERMINISTIC_POLICY_PLANNER");
        root.put("memory_mode", "BE_PERSISTED_EXTERNAL_MEMORY");
        root.put("memory_can_trigger_rule", false);
        root.put("autonomous_risk_decision_allowed", false);
        root.put("trace_is_chain_of_thought", false);
        root.put("decision_support_only", true);
        root.put("final_decision_authority", "현장 지휘관");
        return root;
    }

    private static void putPreviousOrNull(ObjectNode target, String field,
                                          JsonNode previousMemory) {
        JsonNode previous = previousMemory == null ? null : previousMemory.get(field);
        if (previous == null || previous.isNull()) {
            target.putNull(field);
        } else {
            target.set(field, previous.deepCopy());
        }
    }
}
