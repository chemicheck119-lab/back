package com.c2guard.bff.incident;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 신규 행동 카드 모델 API({@code action-brief-v1}) 요청 루트를 조립한다.
 * {@code analysis}는 기존 IncidentAnalyzeRequest를 감싼 것이고, {@code revision}과
 * 취소·상충 필드는 요청 루트에 위치한다 (docs(api) 백엔드 연동 가이드 04항).
 */
@Component
class IncidentBriefRequestMapper {

    private final ObjectMapper objectMapper;

    IncidentBriefRequestMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    ObjectNode map(ObjectNode analysis, long revision, List<String> invalidatedConfirmationIds,
                  Boolean reportedEvidenceConflict) {
        ObjectNode target = objectMapper.createObjectNode();
        target.put("revision", revision);
        target.set("analysis", analysis);
        if (invalidatedConfirmationIds != null && !invalidatedConfirmationIds.isEmpty()) {
            ArrayNode ids = target.putArray("invalidated_confirmation_ids");
            invalidatedConfirmationIds.forEach(ids::add);
        }
        if (reportedEvidenceConflict != null) {
            target.put("reported_evidence_conflict", reportedEvidenceConflict);
        }
        return target;
    }
}
