package com.c2guard.controller;

import com.c2guard.dto.CompatibilityRequest;
import com.c2guard.dto.CompatibilityResponse;
import com.c2guard.service.CompatibilityService;
import com.c2guard.bff.common.BffContractException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CompatibilityController {

    private final CompatibilityService compatibilityService;

    public CompatibilityController(CompatibilityService compatibilityService) {
        this.compatibilityService = compatibilityService;
    }

    /**
     * 두 화학물질의 혼재/반응성 판정.
     * 요청 예: {"substanceA": "황산", "substanceB": "메탄올"}
     */
    @PostMapping("/api/compatibility/check")
    public CompatibilityResponse check(@RequestBody CompatibilityRequest request) {
        throw new BffContractException(410, "LEGACY_SAFETY_GATE_REQUIRED",
                "이 경로는 2-CAS 확인을 강제하지 않아 비활성화되었습니다. 사고 분석 confirmation 경로를 사용하세요.",
                false);
    }
}
