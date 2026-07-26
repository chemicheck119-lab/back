package com.c2guard.controller;

import com.c2guard.dto.IncidentCheckRequest;
import com.c2guard.dto.IncidentCheckResponse;
import com.c2guard.service.IncidentCheckService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 서비스의 핵심 기능: "출동시설 + 사고물질" 입력받아서
 * 시설 보유물질 전체와 사고물질의 반응성을 한 번에 검토해서 돌려준다.
 */
@RestController
public class IncidentCheckController {

    private final IncidentCheckService incidentCheckService;

    public IncidentCheckController(IncidentCheckService incidentCheckService) {
        this.incidentCheckService = incidentCheckService;
    }

    /**
     * 요청 예: {"facilityName": "(주)LG생활건강", "incidentSubstance": "톨루엔"}
     */
    @PostMapping("/api/incident-check")
    public IncidentCheckResponse check(@RequestBody IncidentCheckRequest request) {
        return incidentCheckService.check(request.getFacilityName(), request.getIncidentSubstance());
    }
}
