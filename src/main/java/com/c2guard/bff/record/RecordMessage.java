package com.c2guard.bff.record;

import java.time.OffsetDateTime;

/**
 * 대응 기록에 포함된 대화 1건. 상세 조회에서만 노출한다.
 */
public record RecordMessage(
        String messageId,
        int sequence,
        String role,
        String text,
        OffsetDateTime createdAt,
        String analysisId
) {
}
