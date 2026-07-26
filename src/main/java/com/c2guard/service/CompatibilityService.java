package com.c2guard.service;

import com.c2guard.domain.CompatibilityResult;
import com.c2guard.domain.Substance;
import com.c2guard.dto.CompatibilityResponse;
import org.springframework.stereotype.Service;

/**
 * 두 화학물질의 혼재/반응성 판정.
 * Python 쪽 rule_engine_draft.py의 check_pair()와 1:1로 대응한다.
 * <p>
 * TODO: 최현준 CAMEO 반응성그룹 데이터 연동 후에는, 여기서 별표19 매트릭스를 조회하기 전에
 *       CAMEO 그룹 호환성 조회를 먼저 시도하고, 매칭 안 되는 물질만 별표19로 fallback 하도록 수정.
 *       (현재는 별표19 매트릭스만 있는 v0.2 상태)
 */
@Service
public class CompatibilityService {

    private final SubstanceRegistryService registryService;
    private final HazardMatrixService matrixService;

    public CompatibilityService(SubstanceRegistryService registryService,
                                 HazardMatrixService matrixService) {
        this.registryService = registryService;
        this.matrixService = matrixService;
    }

    public CompatibilityResponse checkPair(String nameA, String nameB) {
        Substance a = registryService.find(nameA);
        Substance b = registryService.find(nameB);

        if (a == null || b == null) {
            String missing = (a == null ? nameA : nameB);
            return new CompatibilityResponse(
                    nameA, nameB, CompatibilityResult.UNKNOWN,
                    "registry에 없는 물질: " + missing,
                    null
            );
        }

        if (!a.hasHazardClass() || !b.hasHazardClass()) {
            String unclassified = !a.hasHazardClass() ? nameA : nameB;
            return new CompatibilityResponse(
                    nameA, nameB, CompatibilityResult.UNCLASSIFIED,
                    "물질은 확인됐지만 유별 미분류: " + unclassified,
                    "위험물제조소 현황 / MSDS 데이터로 유별 분류 필요"
            );
        }

        String verdict = matrixService.lookup(a.getHazardClass(), b.getHazardClass());
        CompatibilityResult result = switch (verdict) {
            case "O" -> CompatibilityResult.COMPATIBLE;
            case "X" -> CompatibilityResult.INCOMPATIBLE;
            case "-" -> CompatibilityResult.SAME_CLASS;
            default -> CompatibilityResult.UNCLASSIFIED;
        };

        return new CompatibilityResponse(
                nameA, nameB, result,
                a.getHazardClass() + " - " + b.getHazardClass() + " 혼재기준",
                "위험물안전관리법 시행령 별표19"
        );
    }
}
