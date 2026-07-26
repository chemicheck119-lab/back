package com.c2guard.controller;

import com.c2guard.dto.IncidentRecordRequest;
import com.c2guard.dto.IncidentRecordResponse;
import com.c2guard.service.IncidentRecordService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 대응충돌검토 화면의 "확인" 버튼(현장 지휘관 최종 판단)을 눌렀을 때 호출되는 API.
 */
@RestController
public class IncidentRecordController {

    private final IncidentRecordService incidentRecordService;

    public IncidentRecordController(IncidentRecordService incidentRecordService) {
        this.incidentRecordService = incidentRecordService;
    }

    @PostMapping("/api/c2guard/records")
    public IncidentRecordResponse save(@RequestBody IncidentRecordRequest request) {
        return incidentRecordService.save(request);
    }

    @GetMapping("/api/c2guard/records")
    public List<IncidentRecordResponse> findAll() {
        return incidentRecordService.findAll();
    }
}