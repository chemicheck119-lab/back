package com.c2guard.bff.record;

import java.time.OffsetDateTime;

/**
 * "대응 기록" 목록 화면 1행에 필요한 요약 정보.
 * 대화 원문과 구조화 상세는 {@link RecordDetail}에서만 노출한다.
 */
public record RecordSummary(
        String recordId,
        String incidentId,
        String facilityName,
        String incidentSubstanceName,
        String briefApplicationStatus,
        String finalResponseOutcome,
        OffsetDateTime savedAt
) {
}
