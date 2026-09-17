package com.c2guard.bff.incident;

import java.util.List;

/**
 * {@link IncidentBriefBffService#brief}의 입력. {@code invalidatedConfirmationIds}는
 * 취소 재요청 시에만 채우며 최대 2개까지 허용한다 (docs(api) 백엔드 연동 가이드 04항).
 */
public record IncidentBriefCommand(
        IncidentAnalyzeRequest analysis,
        List<String> invalidatedConfirmationIds,
        Boolean reportedEvidenceConflict
) {
    public IncidentBriefCommand {
        invalidatedConfirmationIds = invalidatedConfirmationIds == null
                ? List.of() : List.copyOf(invalidatedConfirmationIds);
        if (invalidatedConfirmationIds.size() > 2) {
            throw new IllegalArgumentException(
                    "invalidatedConfirmationIds는 최대 2개까지 지정할 수 있습니다.");
        }
    }
}
