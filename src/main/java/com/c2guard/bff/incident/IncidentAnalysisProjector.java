package com.c2guard.bff.incident;

import com.c2guard.bff.common.BffContractException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

@Component
class IncidentAnalysisProjector {

    static final String BFF_SCHEMA = "chemicheck119-dashboard-bff-v1";
    static final String MODEL_SCHEMA = "chemiguard119-api-v1";

    private static final String FACILITY_HISTORY_LABEL = "과거 공개 이력 기반 시설물질 후보";
    private static final String FACILITY_HISTORY_SEMANTICS =
            "HISTORICAL_CANDIDATE_NOT_CURRENT_INVENTORY";
    private static final Set<String> AWAITING_STATES = Set.of(
            "AWAITING_SUBSTANCE_CONFIRMATION",
            "AWAITING_INCIDENT_CONFIRMATION",
            "AWAITING_FACILITY_CONFIRMATION"
    );
    private static final Set<String> INCONCLUSIVE_STATES = Set.of(
            "VERIFY_REQUIRED", "UNCLASSIFIED", "CAMEO_GROUP_SCREENING_ONLY"
    );

    private final ObjectMapper objectMapper;

    IncidentAnalysisProjector(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    ObjectNode project(JsonNode source, String expectedRequestId, String expectedIncidentId) {
        requireObject(source, "root");
        requireTextEquals(source, "schema_version", MODEL_SCHEMA);
        requireTextEquals(source, "request_id", expectedRequestId);
        requireTextEquals(source, "incident_id", expectedIncidentId);

        String state = requireText(source, "state");
        if (!AWAITING_STATES.contains(state)
                && !INCONCLUSIVE_STATES.contains(state)
                && !"SCREENING_COMPLETED".equals(state)) {
            throw violation("지원하지 않는 모델 분석 상태입니다.");
        }

        ObjectNode target = objectMapper.createObjectNode();
        target.put("schemaVersion", BFF_SCHEMA);
        target.put("sourceModelSchemaVersion", MODEL_SCHEMA);
        copyRequired(target, "analysisId", source, "analysis_id");
        target.put("requestId", expectedRequestId);
        target.put("incidentId", expectedIncidentId);
        target.put("state", state);

        JsonNode modelOutputs = requireObjectField(source, "model_outputs");
        target.set("parser", projectParser(requireObjectField(modelOutputs, "parser")));
        target.set("substanceCandidates", projectSubstanceCandidates(
                requireArrayField(modelOutputs, "substance_candidates")));
        target.set("facilityHistory", projectFacilityHistory(
                requireObjectField(modelOutputs, "facility_history_candidates")));
        target.set("evidenceCards", projectEvidence(requireArrayField(source, "evidence")));

        JsonNode groundedRag = source.get("grounded_rag");
        if (groundedRag != null) {
            target.set("groundedRag", groundedRag.isNull()
                    ? groundedRag.deepCopy()
                    : projectGroundedRag(requireObject(groundedRag, "grounded_rag")));
            validateGroundedRag(target.get("groundedRag"), state);
        }
        JsonNode agent = source.get("agent");
        if (agent != null) {
            target.set("agent", agent.isNull() ? agent.deepCopy() : toCamelCase(agent));
        }

        ObjectNode gate = projectConfirmationGate(requireObjectField(source, "confirmation_gate"));
        target.set("confirmationGate", gate);
        ObjectNode conflictReview = projectConflictReview(
                requireObjectField(source, "conflict_review"), state, gate);
        target.set("conflictReview", conflictReview);
        target.put("riskDisplayAllowed", conflictReview.path("riskDisplayAllowed").asBoolean(false));
        target.set("requiredNextSteps", toCamelCase(requireArrayField(source, "required_next_steps")));
        target.set("provenance", projectProvenance(requireObjectField(source, "provenance")));
        copyRequired(target, "safetyNotice", source, "safety_notice");
        return target;
    }

    private ObjectNode projectParser(JsonNode source) {
        ObjectNode target = objectMapper.createObjectNode();
        copyRequired(target, "backend", source, "backend");
        copyRequired(target, "incidentTypes", source, "incident_types");
        copyRequired(target, "substanceMentions", source, "substance_mentions");
        copyRequired(target, "warning", source, "warning");
        return (ObjectNode) toCamelCase(target);
    }

    private ArrayNode projectSubstanceCandidates(ArrayNode source) {
        ArrayNode target = objectMapper.createArrayNode();
        source.forEach(groupNode -> {
            JsonNode group = requireObject(groupNode, "substance candidate group");
            if (!group.path("requires_responder_confirmation").asBoolean(false)) {
                throw violation("현장 확인이 필요하지 않은 물질 후보는 화면에 표시할 수 없습니다.");
            }
            ObjectNode projectedGroup = target.addObject();
            copyRequired(projectedGroup, "surfaceText", group, "surface_text");
            copyRequired(projectedGroup, "role", group, "role");
            copyRequired(projectedGroup, "resolverStatus", group, "resolver_status");
            ArrayNode projectedCandidates = projectedGroup.putArray("candidates");
            requireArrayField(group, "candidates").forEach(candidateNode -> {
                JsonNode candidate = requireObject(candidateNode, "resolver candidate");
                if (candidate.path("rule_eligible").asBoolean(false)
                        || candidate.path("current_inventory_confirmed").asBoolean(false)) {
                    throw violation("확인 전 후보를 Rule 실행 또는 현재 재고로 표시할 수 없습니다.");
                }
                ObjectNode projected = projectedCandidates.addObject();
                copyRequired(projected, "casNumber", candidate, "cas_number");
                copyOptional(projected, "rankingScore", candidate, "score");
                projected.put("rankingScoreIsProbability", false);
                projected.put("ruleEligible", false);
                projected.put("currentInventoryConfirmed", false);
            });
            projectedGroup.put("requiresResponderConfirmation", true);
        });
        return target;
    }

    private ObjectNode projectFacilityHistory(JsonNode source) {
        ObjectNode target = objectMapper.createObjectNode();
        copyRequired(target, "status", source, "status");
        target.put("label", FACILITY_HISTORY_LABEL);
        target.put("semantics", FACILITY_HISTORY_SEMANTICS);
        copyRequired(target, "warning", source, "warning");
        ArrayNode candidates = target.putArray("candidates");
        requireArrayField(source, "results").forEach(candidateNode -> {
            JsonNode candidate = requireObject(candidateNode, "facility candidate");
            ObjectNode projected = candidates.addObject();
            copyRequired(projected, "facilityName", candidate, "facility_name");
            copyOptional(projected, "address", candidate, "address");
            copyOptional(projected, "province", candidate, "province");
            copyRequired(projected, "casNumber", candidate, "cas_number");
            copyOptional(projected, "chemicalNames", candidate, "chemical_names");
            copyOptional(projected, "latestSurveyYear", candidate, "latest_survey_year");
            copyOptional(projected, "sourceUrl", candidate, "source_url");
            projected.put("evidenceClass", "REPORTED_HANDLING_HISTORY");
            projected.put("currentInventoryConfirmed", false);
            projected.put("ruleEligible", false);
            projected.put("requiresOnSiteConfirmation", true);
        });
        return target;
    }

    private ArrayNode projectEvidence(ArrayNode source) {
        ArrayNode target = objectMapper.createArrayNode();
        source.forEach(cardNode -> {
            JsonNode card = requireObject(cardNode, "evidence card");
            ObjectNode projected = target.addObject();
            copyRequired(projected, "evidenceId", card, "evidence_id");
            copyRequired(projected, "casNumber", card, "cas_number");
            copyRequired(projected, "source", card, "source");
            copyRequired(projected, "title", card, "title");
            projected.put("bodyLabel", "공식 문서 발췌");
            copyRequired(projected, "bodyPreview", card, "body_preview");
            copyRequired(projected, "sourceUrl", card, "source_url");
            copyRequired(projected, "documentVersion", card, "document_version");
            copyOptional(projected, "casLinkStatus", card, "cas_link_status");
        });
        return target;
    }

    private ObjectNode projectGroundedRag(JsonNode source) {
        ObjectNode target = objectMapper.createObjectNode();
        copyRequired(target, "schemaVersion", source, "schema_version");
        copyRequired(target, "status", source, "status");
        copyRequired(target, "usedLlm", source, "used_llm");
        copyOptional(target, "model", source, "model");
        target.set("statements", projectRagStatements(requireArrayField(source, "statements")));
        target.set("citations", projectRagCitations(requireArrayField(source, "citations")));
        copyRequired(target, "riskDecisionSource", source, "risk_decision_source");
        copyRequired(target, "semanticGroundingVerified", source, "semantic_grounding_verified");
        copyOptional(target, "fallbackReason", source, "fallback_reason");
        ArrayNode limitations = target.putArray("limitations");
        requireArrayField(source, "limitations").forEach(item -> {
            if (!item.asText().contains("인용 ID 검증")) {
                limitations.add(item.deepCopy());
            }
        });
        return target;
    }

    private void validateGroundedRag(JsonNode groundedRag, String state) {
        if (groundedRag == null || groundedRag.isNull()) {
            return;
        }
        String status = requireText(groundedRag, "status");
        if (AWAITING_STATES.contains(state)
                && !"NOT_RUN_REQUIRES_CONFIRMED_PAIR".equals(status)) {
            throw violation("확인 대기 상태에서 근거 요약을 실행할 수 없습니다.");
        }
        if (INCONCLUSIVE_STATES.contains(state)
                && !"NOT_RUN_RULE_NOT_COMPLETED".equals(status)) {
            throw violation("미분류 Rule 결과에서 완료된 근거 요약을 표시할 수 없습니다.");
        }
        if ("SCREENING_COMPLETED".equals(state)
                && ("NOT_RUN_REQUIRES_CONFIRMED_PAIR".equals(status)
                || "NOT_RUN_RULE_NOT_COMPLETED".equals(status))) {
            throw violation("완료된 Rule 결과의 근거 요약 상태가 올바르지 않습니다.");
        }
    }

    private ArrayNode projectRagStatements(ArrayNode source) {
        ArrayNode target = objectMapper.createArrayNode();
        source.forEach(statementNode -> {
            JsonNode statement = requireObject(statementNode, "RAG statement");
            ObjectNode projected = target.addObject();
            copyRequired(projected, "text", statement, "text");
            copyRequired(projected, "sourceIds", statement, "source_ids");
        });
        return target;
    }

    private ArrayNode projectRagCitations(ArrayNode source) {
        ArrayNode target = objectMapper.createArrayNode();
        source.forEach(citationNode -> {
            JsonNode citation = requireObject(citationNode, "RAG citation");
            ObjectNode projected = target.addObject();
            copyRequired(projected, "sourceId", citation, "source_id");
            copyRequired(projected, "sourceType", citation, "source_type");
            copyRequired(projected, "title", citation, "title");
            copyOptional(projected, "casNumber", citation, "cas_number");
            copyRequired(projected, "sourceUrls", citation, "source_urls");
        });
        return target;
    }

    private ObjectNode projectConfirmationGate(JsonNode source) {
        ObjectNode target = objectMapper.createObjectNode();
        copyRequired(target, "policy", source, "policy");
        copyRequired(target, "incidentConfirmed", source, "incident_confirmed");
        copyRequired(target, "facilityConfirmed", source, "facility_confirmed");
        copyRequired(target, "allRequiredConfirmed", source, "all_required_confirmed");
        copyRequired(target, "ruleExecutionAllowed", source, "rule_execution_allowed");
        boolean both = target.path("incidentConfirmed").asBoolean()
                && target.path("facilityConfirmed").asBoolean();
        if (target.path("allRequiredConfirmed").asBoolean() != both
                || target.path("ruleExecutionAllowed").asBoolean() != both) {
            throw violation("모델 confirmation gate 상태가 역할별 확인과 일치하지 않습니다.");
        }
        return target;
    }

    private ObjectNode projectConflictReview(JsonNode source, String state, ObjectNode gate) {
        boolean executed = requireBoolean(source, "executed");
        if (!executed) {
            if (!AWAITING_STATES.contains(state) || gate.path("allRequiredConfirmed").asBoolean()) {
                throw violation("확인 대기 상태와 Rule 실행 gate가 일치하지 않습니다.");
            }
            ObjectNode target = objectMapper.createObjectNode();
            target.put("executed", false);
            copyRequired(target, "status", source, "status");
            copyRequired(target, "missingConfirmations", source, "missing_confirmations");
            copyRequired(target, "reason", source, "reason");
            target.put("riskDisplayAllowed", false);
            return target;
        }

        if (!gate.path("allRequiredConfirmed").asBoolean()
                || !gate.path("ruleExecutionAllowed").asBoolean()) {
            throw violation("두 물질 확인 없이 실행된 Rule 결과는 표시할 수 없습니다.");
        }
        String status = requireText(source, "status");
        ObjectNode target = objectMapper.createObjectNode();
        target.put("executed", true);
        target.put("status", status);
        JsonNode result = requireObjectField(source, "result");
        if ("SCREENING_COMPLETED".equals(status)) {
            if (!"SCREENING_COMPLETED".equals(state)) {
                throw violation("분석 상태와 완료된 Rule 결과가 일치하지 않습니다.");
            }
            target.set("result", projectCompletedResult(result));
            target.put("riskDisplayAllowed", true);
            return target;
        }
        if (!INCONCLUSIVE_STATES.contains(status) || !status.equals(state)) {
            throw violation("분석 상태와 미분류 Rule 결과가 일치하지 않습니다.");
        }
        ObjectNode inconclusive = objectMapper.createObjectNode();
        inconclusive.put("kind", "INCONCLUSIVE_RESULT");
        inconclusive.put("status", status);
        copyRequired(inconclusive, "reason", result, "reason");
        inconclusive.put("humanConfirmationRequired", true);
        target.set("result", inconclusive);
        target.put("riskDisplayAllowed", false);
        return target;
    }

    private ObjectNode projectCompletedResult(JsonNode source) {
        requireObject(source, "completed result");
        ObjectNode target = objectMapper.createObjectNode();
        target.put("kind", "ORDINAL_SCREENING_RESULT");
        copyRequired(target, "status", source, "status");
        copyRequired(target, "scope", source, "scope");
        copyRequired(target, "policyMode", source, "policy_mode");
        copyRequired(target, "incidentCas", source, "incident_cas");
        copyRequired(target, "facilityCas", source, "facility_cas");
        copyRequired(target, "ruleId", source, "rule_id");
        copyRequired(target, "ruleVersion", source, "rule_version");
        copyRequired(target, "severity", source, "severity");
        copyRequired(target, "riskLevel", source, "risk_level");
        copyRequired(target, "riskLevelKo", source, "risk_level_ko");
        target.set("riskScale", toCamelCase(requireObjectField(source, "risk_scale")));
        copyRequired(target, "hazardCodes", source, "hazard_codes");
        copyOptional(target, "gasProducts", source, "gas_products");
        copyRequired(target, "briefText", source, "brief_text");
        copyRequired(target, "requiredChecks", source, "required_checks");
        copyRequired(target, "evidenceUrls", source, "evidence_urls");
        copyRequired(target, "limitations", source, "limitations");
        copyRequired(target, "finalDecision", source, "final_decision");
        copyRequired(target, "expertReviewed", source, "expert_reviewed");
        copyRequired(target, "humanConfirmationRequired", source,
                "human_confirmation_required");
        target.set("mappingProvenance",
                toCamelCase(requireArrayField(source, "mapping_provenance")));
        target.set("evidenceProvenance",
                toCamelCase(requireObjectField(source, "evidence_provenance")));
        ObjectNode referenceAssurance = (ObjectNode) toCamelCase(
                requireObjectField(source, "reference_assurance"));
        normalizeUtc(referenceAssurance, "reviewedAtUtc");
        target.set("referenceAssurance", referenceAssurance);

        requireTextEquals(target, "status", "SCREENING_COMPLETED");
        requireTextEquals(target, "scope", "PUBLIC_SOURCE_CAMEO_SCREENING");
        requireTextEquals(target, "policyMode", "PUBLIC_SOURCE_PILOT_V1");
        if (target.path("expertReviewed").asBoolean(true)) {
            throw violation("전문가 검토가 없는 결과를 승인 결과로 표시할 수 없습니다.");
        }
        JsonNode riskScaleNode = requireObjectField(target, "riskScale");
        if (riskScaleNode.path("isProbability").asBoolean(true)
                || !riskScaleNode.path("probabilityPercent").isNull()) {
            throw violation("서수 위험등급을 확률로 표시할 수 없습니다.");
        }
        ((ObjectNode) riskScaleNode).put("lowMeansSafe", false);
        return target;
    }

    private static void normalizeUtc(ObjectNode source, String field) {
        JsonNode value = source.get(field);
        if (value == null || !value.isTextual()) {
            throw violation("모델 응답의 " + field + " 시각이 없습니다.");
        }
        try {
            String normalized = OffsetDateTime.parse(value.asText())
                    .withOffsetSameInstant(ZoneOffset.UTC)
                    .format(DateTimeFormatter.ISO_INSTANT);
            source.put(field, normalized);
        } catch (RuntimeException invalidTime) {
            throw violation("모델 응답의 " + field + " 시각 형식이 올바르지 않습니다.");
        }
    }

    private ObjectNode projectProvenance(JsonNode source) {
        ObjectNode target = objectMapper.createObjectNode();
        copyRequired(target, "modelVersion", source, "model_version");
        copyRequired(target, "dataVersion", source, "data_version");
        copyRequired(target, "rulePolicy", source, "rule_policy");
        copyRequired(target, "expertReviewed", source, "expert_reviewed");
        copyRequired(target, "finalDecisionAuthority", source, "final_decision_authority");
        if (target.path("expertReviewed").asBoolean(true)) {
            throw violation("현재 BFF v1은 expertReviewed=false 계약만 지원합니다.");
        }
        return target;
    }

    private JsonNode toCamelCase(JsonNode source) {
        if (source.isArray()) {
            ArrayNode target = objectMapper.createArrayNode();
            source.forEach(value -> target.add(toCamelCase(value)));
            return target;
        }
        if (!source.isObject()) {
            return source.deepCopy();
        }
        ObjectNode target = objectMapper.createObjectNode();
        Iterator<Map.Entry<String, JsonNode>> fields = source.fields();
        fields.forEachRemaining(entry ->
                target.set(snakeToCamel(entry.getKey()), toCamelCase(entry.getValue())));
        return target;
    }

    private static String snakeToCamel(String value) {
        StringBuilder result = new StringBuilder();
        boolean uppercase = false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '_') {
                uppercase = true;
            } else if (uppercase) {
                result.append(Character.toUpperCase(character));
                uppercase = false;
            } else {
                result.append(character);
            }
        }
        return result.toString();
    }

    private static JsonNode requireObject(JsonNode source, String label) {
        if (source == null || !source.isObject()) {
            throw violation("모델 응답의 " + label + " 객체가 없습니다.");
        }
        return source;
    }

    private static JsonNode requireObjectField(JsonNode source, String field) {
        return requireObject(source.get(field), field);
    }

    private static ArrayNode requireArrayField(JsonNode source, String field) {
        JsonNode value = source.get(field);
        if (!(value instanceof ArrayNode array)) {
            throw violation("모델 응답의 " + field + " 배열이 없습니다.");
        }
        return array;
    }

    private static String requireText(JsonNode source, String field) {
        JsonNode value = source.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw violation("모델 응답의 " + field + " 문자열이 없습니다.");
        }
        return value.asText();
    }

    private static void requireTextEquals(JsonNode source, String field, String expected) {
        if (!expected.equals(requireText(source, field))) {
            throw violation("모델 응답의 " + field + " 값이 계약과 다릅니다.");
        }
    }

    private static boolean requireBoolean(JsonNode source, String field) {
        JsonNode value = source.get(field);
        if (value == null || !value.isBoolean()) {
            throw violation("모델 응답의 " + field + " boolean이 없습니다.");
        }
        return value.asBoolean();
    }

    private static void copyRequired(ObjectNode target, String targetField,
                                     JsonNode source, String sourceField) {
        JsonNode value = source.get(sourceField);
        if (value == null || value.isMissingNode() || value.isNull()) {
            throw violation("모델 응답의 " + sourceField + " 값이 없습니다.");
        }
        target.set(targetField, value.deepCopy());
    }

    private static void copyOptional(ObjectNode target, String targetField,
                                     JsonNode source, String sourceField) {
        JsonNode value = source.get(sourceField);
        if (value != null && !value.isMissingNode()) {
            target.set(targetField, value.deepCopy());
        }
    }

    private static BffContractException violation(String message) {
        return new BffContractException(422, "MODEL_CONTRACT_VIOLATION", message, false);
    }
}
