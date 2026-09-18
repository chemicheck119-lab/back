package com.c2guard.bff.incident;

import com.c2guard.bff.common.BffContractException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 모델 API가 실제로 {@code action-brief-v1} 계약을 지켰는지 검증한다.
 * <p>
 * {@link com.c2guard.integration.model.RestModelApiClient}의 schema_version 검사만으로는
 * {@code {"schema_version":"action-brief-v1"}}처럼 알맹이가 없는 응답도 통과한다.
 * 그리고 {@code ModelApiResponse.requestId()}는 우리가 보낸 값을 그대로 돌려주는 것이라
 * 실제 업스트림 응답 본문의 request_id 불일치를 잡아내지 못한다 — 이 두 가지를
 * 여기서 본문을 직접 검사해 막는다 (Codex 리뷰 P1, PR #45).
 */
@Component
class IncidentBriefResponseValidator {

    private static final Set<String> PHASES = Set.of("initial", "final");
    private static final Set<String> STATUSES = Set.of(
            "PENDING", "NEEDS_CONFIRMATION", "COMPLETED", "HELD", "TIMEOUT");

    JsonNode validate(JsonNode source, String expectedRequestId) {
        ObjectNode root = requireObject(source, "root");
        requireTextEquals(root, "request_id", expectedRequestId);

        String phase = requireText(root, "phase");
        if (!PHASES.contains(phase)) {
            throw violation("action-brief 응답의 phase 값이 올바르지 않습니다.");
        }
        String status = requireText(root, "status");
        if (!STATUSES.contains(status)) {
            throw violation("action-brief 응답의 status 값이 올바르지 않습니다.");
        }

        ObjectNode confirmationState = requireObject(root.get("confirmation_state"),
                "confirmation_state");
        requireBoolean(confirmationState, "INCIDENT");
        requireBoolean(confirmationState, "FACILITY");

        ObjectNode ruleReview = requireObject(root.get("rule_review"), "rule_review");
        requireBoolean(ruleReview, "executed");

        if ("final".equals(phase)) {
            requireArray(root, "cards");
            requireArray(root, "sources");
        }

        return source;
    }

    private static ObjectNode requireObject(JsonNode source, String label) {
        if (!(source instanceof ObjectNode object)) {
            throw violation("action-brief 응답의 " + label + " 객체가 없습니다.");
        }
        return object;
    }

    private static ArrayNode requireArray(JsonNode source, String field) {
        JsonNode value = source.get(field);
        if (!(value instanceof ArrayNode array)) {
            throw violation("action-brief 응답의 " + field + " 배열이 없습니다.");
        }
        return array;
    }

    private static String requireText(JsonNode source, String field) {
        JsonNode value = source.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw violation("action-brief 응답의 " + field + " 문자열이 없습니다.");
        }
        return value.asText();
    }

    private static void requireTextEquals(JsonNode source, String field, String expected) {
        if (!expected.equals(requireText(source, field))) {
            throw violation("action-brief 응답의 " + field + "가 요청과 일치하지 않습니다.");
        }
    }

    private static void requireBoolean(JsonNode source, String field) {
        JsonNode value = source.get(field);
        if (value == null || !value.isBoolean()) {
            throw violation("action-brief 응답의 " + field + " boolean이 없습니다.");
        }
    }

    private static BffContractException violation(String message) {
        return new BffContractException(422, "MODEL_CONTRACT_VIOLATION", message, false);
    }
}
