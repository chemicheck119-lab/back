package com.c2guard.bff.incident;

import com.c2guard.bff.common.BffContractException;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

final class IncidentBriefResponseValidator {

    JsonNode validate(JsonNode body, String requestId, String incidentId, long revision) {
        if (body == null || !body.isObject()
                || !"action-brief-v1".equals(body.path("schema_version").asText())
                || !requestId.equals(body.path("request_id").asText())
                || !incidentId.equals(body.path("incident_id").asText())
                || revision != body.path("revision").asLong(-1)
                || !body.path("state_fingerprint").isTextual()
                || !body.path("phase").isTextual()
                || !body.path("status").isTextual()
                || !body.path("summary").isTextual()
                || !body.path("cards").isArray()
                || !body.path("missing_information").isArray()
                || !body.path("sources").isArray()
                || !body.path("confirmation_state").isObject()
                || !body.path("versions").isObject()) {
            throw new BffContractException(422, "MODEL_CONTRACT_VIOLATION",
                    "action-brief 응답의 필수 계약 또는 요청 binding이 유효하지 않습니다.", false);
        }

        JsonNode confirmationState = body.path("confirmation_state");
        if (!confirmationState.path("INCIDENT").isBoolean()
                || !confirmationState.path("FACILITY").isBoolean()
                || !confirmationState.path("INCIDENT").asBoolean()
                || !confirmationState.path("FACILITY").asBoolean()) {
            throw new BffContractException(422, "MODEL_CONTRACT_VIOLATION",
                    "action-brief 응답의 두 CAS confirmation 상태가 유효하지 않습니다.", false);
        }
        return body;
    }
}
