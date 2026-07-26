package com.c2guard.service;

import com.c2guard.domain.CompatibilityResult;
import com.c2guard.domain.FacilityRecord;
import com.c2guard.dto.CompatibilityResponse;
import com.c2guard.dto.IncidentCheckResponse;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 기획서 서비스 흐름의 6~7단계에 대응:
 * "시설 보유 화학물질 조회" (FacilityService) + "Rule-Based 대응 충돌 검토" (CompatibilityService) 를 묶는다.
 * <p>
 * 흐름: 시설명으로 보유물질 목록 조회 → 사고물질과 보유물질 하나하나를 CompatibilityService로 대조
 *       → 하나라도 INCOMPATIBLE 이면 hasIncompatible=true 로 표시 (프론트에서 경고 배너용)
 */
@Service
public class IncidentCheckService {

    private final FacilityService facilityService;
    private final CompatibilityService compatibilityService;

    public IncidentCheckService(FacilityService facilityService,
                                 CompatibilityService compatibilityService) {
        this.facilityService = facilityService;
        this.compatibilityService = compatibilityService;
    }

    public IncidentCheckResponse check(String facilityName, String incidentSubstance) {
        List<FacilityRecord> records = facilityService.findByFacilityName(facilityName);
        boolean facilityFound = !records.isEmpty();

        // 같은 시설이 같은 물질을 여러 줄로 갖고 있을 수 있어서(연도별 등) 물질명 기준 중복 제거
        Set<String> heldSubstances = new LinkedHashSet<>();
        for (FacilityRecord r : records) {
            heldSubstances.add(r.getSubstanceName());
        }

        List<CompatibilityResponse> results = heldSubstances.stream()
                .map(held -> compatibilityService.checkPair(incidentSubstance, held))
                .toList();

        boolean hasIncompatible = results.stream()
                .anyMatch(r -> r.getResult() == CompatibilityResult.INCOMPATIBLE);

        return new IncidentCheckResponse(
                facilityName, incidentSubstance, heldSubstances.size(),
                facilityFound, hasIncompatible, results
        );
    }
}
