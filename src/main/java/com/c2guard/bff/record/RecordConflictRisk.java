package com.c2guard.bff.record;

import java.util.List;

/**
 * 기록 저장 시점에 결합된 RuleEngine 충돌 검토 결과.
 * 두 CAS가 모두 확인된 경우에만 저장되므로 기록에 없을 수 있다({@code null}).
 */
public record RecordConflictRisk(
        String analysisId,
        String incidentCas,
        String facilitySubstanceName,
        String facilitySubstanceCas,
        String ruleId,
        String ruleVersion,
        String severity,
        String riskLevel,
        String riskLevelKo,
        String briefText,
        boolean expertReviewed,
        boolean humanConfirmationRequired,
        List<String> hazardCodes,
        List<String> gasProducts
) {
}
