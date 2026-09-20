package com.c2guard.controller;

import com.c2guard.dto.IncidentCheckRequest;
import com.c2guard.dto.IncidentCheckResponse;
import com.c2guard.service.IncidentCheckService;
import com.c2guard.bff.common.BffContractException;
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
        throw new BffContractException(410, "LEGACY_SAFETY_GATE_REQUIRED",
                "이 경로는 2-CAS 확인을 강제하지 않아 비활성화되었습니다. 사고 분석 confirmation 경로를 사용하세요.",
                false);
    }
}
