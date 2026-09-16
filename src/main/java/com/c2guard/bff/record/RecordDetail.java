package com.c2guard.bff.record;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 대응 기록 상세 화면에 필요한 전체 정보.
 * {@link ResponseRecordStore#findDetail(String)}에서 여러 테이블을 조합해 만든다.
 */
public record RecordDetail(
        String recordId,
        String incidentId,
        OffsetDateTime conversationStartedAt,
        OffsetDateTime savedAt,
        String facilityName,
        String facilityAddress,
        String incidentSubstanceName,
        String incidentSubstanceCas,
        String briefApplicationStatus,
        List<String> performedActions,
        List<String> additionalFactors,
        String finalResponseOutcome,
        RecordConflictRisk conflictRisk,
        List<RecordMessage> messages
) {
}
