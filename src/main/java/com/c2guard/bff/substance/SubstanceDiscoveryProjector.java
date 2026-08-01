package com.c2guard.bff.substance;

import com.c2guard.bff.common.BffContractException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
class SubstanceDiscoveryProjector {

    private static final String BFF_SCHEMA = "chemicheck119-dashboard-bff-v1";
    private static final String MODEL_SCHEMA = "chemiguard119-api-v1";
    private static final String PROPERTY_SOURCE_LABEL =
            "소방청 울산 화학물질 정보 기반 관찰 후보";
    private static final Set<String> ALLOWED_STATUS = Set.of(
            "CANDIDATES_FOUND", "NO_RELIABLE_CANDIDATE", "PROFILE_INDEX_NOT_AVAILABLE"
    );
    private static final Set<String> ALLOWED_SEARCH_MODE = Set.of(
            "IDENTITY_AND_PROPERTY_RETRIEVAL", "IDENTITY_RETRIEVAL",
            "PROPERTY_PROFILE_RETRIEVAL", "ABSTAINED"
    );

    private final ObjectMapper objectMapper;

    SubstanceDiscoveryProjector(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    ObjectNode project(JsonNode source, String requestId, String expectedQuery) {
        if (source == null || !source.isObject()) {
            throw violation("모델 물질검색 응답 객체가 없습니다.");
        }
        requireTextEquals(source, "schema_version", MODEL_SCHEMA);
        requireTextEquals(source, "query", expectedQuery);
        String status = requireText(source, "status");
        String searchMode = requireText(source, "search_mode");
        if (!ALLOWED_STATUS.contains(status) || !ALLOWED_SEARCH_MODE.contains(searchMode)) {
            throw violation("모델 물질검색 상태가 BFF v1 계약과 다릅니다.");
        }
        if (!source.path("requires_responder_confirmation").asBoolean(false)
                || source.path("rule_eligible").asBoolean(true)
                || source.path("risk_determination_allowed").asBoolean(true)
                || source.path("candidate_score_is_probability").asBoolean(true)) {
            throw violation("확인 전 물질 후보의 안전 플래그가 올바르지 않습니다.");
        }

        ArrayNode candidates = requireArray(source, "candidates");
        if (("CANDIDATES_FOUND".equals(status)) != !candidates.isEmpty()) {
            throw violation("물질검색 상태와 후보 목록이 일치하지 않습니다.");
        }
        if ("PROFILE_INDEX_NOT_AVAILABLE".equals(status)
                && source.path("profile_index_available").asBoolean(true)) {
            throw violation("성상 검색 인덱스 상태가 응답 status와 일치하지 않습니다.");
        }

        ObjectNode target = objectMapper.createObjectNode();
        target.put("schemaVersion", BFF_SCHEMA);
        target.put("sourceModelSchemaVersion", MODEL_SCHEMA);
        target.put("requestId", requestId);
        target.put("query", expectedQuery);
        target.put("status", status);
        target.put("searchMode", searchMode);
        target.set("candidates", projectCandidates(candidates));
        target.put("requiresResponderConfirmation", true);
        target.put("candidateScoreIsProbability", false);
        target.put("riskDisplayAllowed", false);
        target.put("noReliableCandidateMeansAbsent", false);
        target.put("noReliableCandidateMeansSafe", false);
        copyRequired(target, "notice", source, "notice");
        copyRequired(target, "safetyNotice", source, "safety_notice");
        return target;
    }

    private ArrayNode projectCandidates(ArrayNode source) {
        ArrayNode target = objectMapper.createArrayNode();
        source.forEach(candidateNode -> {
            if (!candidateNode.isObject()) {
                throw violation("모델 물질 후보 객체가 올바르지 않습니다.");
            }
            if (!candidateNode.path("requires_responder_confirmation").asBoolean(false)
                    || candidateNode.path("rule_eligible").asBoolean(true)
                    || candidateNode.path("risk_determination_allowed").asBoolean(true)) {
                throw violation("확인 전 물질 후보를 Rule 결과로 표시할 수 없습니다.");
            }
            ObjectNode candidate = target.addObject();
            copyRequired(candidate, "rank", candidateNode, "rank");
            copyRequired(candidate, "casNumber", candidateNode, "cas_number");
            copyRequired(candidate, "displayName", candidateNode, "display_name");
            copyRequired(candidate, "matchBasis", candidateNode, "match_basis");
            copyOptional(candidate, "matchedExpression", candidateNode, "matched_expression");
            copyRequired(candidate, "matchedProperties", candidateNode, "matched_properties");
            candidate.set("propertySource", projectPropertySource(candidateNode.get("property_profile")));
            copyRequired(candidate, "evidenceStatus", candidateNode, "evidence_status");
            copyOptional(candidate, "evidenceWarning", candidateNode, "evidence_warning");
            copyOptional(candidate, "evidenceNotice", candidateNode, "evidence_notice");
            copyOptional(candidate, "casLinkWarning", candidateNode, "cas_link_warning");
            candidate.set("evidenceCards", projectEvidence(
                    requireArray(candidateNode, "evidence"),
                    candidate.path("casNumber").asText()));
            candidate.put("requiresResponderConfirmation", true);
            candidate.put("ruleEligible", false);
            candidate.put("riskDeterminationAllowed", false);
        });
        return target;
    }

    private JsonNode projectPropertySource(JsonNode source) {
        if (source == null || source.isNull()) {
            return objectMapper.nullNode();
        }
        if (!source.isObject()) {
            throw violation("모델 물질 성상 출처가 올바르지 않습니다.");
        }
        requireTextEquals(source, "source_id", "NFA_ULSAN_CHEMICAL_INFORMATION");
        ObjectNode target = objectMapper.createObjectNode();
        target.put("label", PROPERTY_SOURCE_LABEL);
        copyRequired(target, "sourceId", source, "source_id");
        copyRequired(target, "sourceUrl", source, "source_url");
        copyRequired(target, "documentVersion", source, "document_version");
        return target;
    }

    private ArrayNode projectEvidence(ArrayNode source, String candidateCas) {
        ArrayNode target = objectMapper.createArrayNode();
        source.forEach(cardNode -> {
            if (!cardNode.isObject()) {
                throw violation("모델 근거 카드 객체가 올바르지 않습니다.");
            }
            requireTextEquals(cardNode, "cas_number", candidateCas);
            ObjectNode card = target.addObject();
            copyRequired(card, "evidenceId", cardNode, "evidence_id");
            copyRequired(card, "casNumber", cardNode, "cas_number");
            copyRequired(card, "source", cardNode, "source");
            copyRequired(card, "title", cardNode, "title");
            card.put("bodyLabel", "공식 문서 발췌");
            copyRequired(card, "bodyPreview", cardNode, "body_preview");
            copyRequired(card, "sourceUrl", cardNode, "source_url");
            copyRequired(card, "documentVersion", cardNode, "document_version");
            copyOptional(card, "casLinkStatus", cardNode, "cas_link_status");
        });
        return target;
    }

    private static ArrayNode requireArray(JsonNode source, String field) {
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

    private static void copyRequired(ObjectNode target, String targetField,
                                     JsonNode source, String sourceField) {
        JsonNode value = source.get(sourceField);
        if (value == null || value.isNull() || value.isMissingNode()) {
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
