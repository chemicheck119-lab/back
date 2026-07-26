package com.c2guard.controller;

import com.c2guard.dto.CompatibilityRequest;
import com.c2guard.dto.CompatibilityResponse;
import com.c2guard.service.CompatibilityService;
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
        return compatibilityService.checkPair(request.getSubstanceA(), request.getSubstanceB());
    }
}
