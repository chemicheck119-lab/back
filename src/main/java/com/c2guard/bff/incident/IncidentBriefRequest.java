package com.c2guard.bff.incident;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * {@code POST /api/c2guard/v1/incidents/brief}의 요청 바디.
 * {@code analysis}는 기존 {@link IncidentAnalyzeRequest}를 그대로 재사용한다
 * (docs(api) 백엔드 연동 가이드 04항 — 기존 IncidentAnalyzeRequest를 analysis로 감싼다).
 */
public record IncidentBriefRequest(
        @Valid @NotNull IncidentAnalyzeRequest analysis,
        @Size(max = 2) List<@NotBlank @Size(max = 128) String> invalidatedConfirmationIds,
        Boolean reportedEvidenceConflict
) {
    public IncidentBriefRequest {
        invalidatedConfirmationIds = invalidatedConfirmationIds == null
                ? List.of() : List.copyOf(invalidatedConfirmationIds);
    }
}
