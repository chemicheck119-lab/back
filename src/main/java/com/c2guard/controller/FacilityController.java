package com.c2guard.controller;

import com.c2guard.domain.FacilityRecord;
import com.c2guard.service.FacilityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class FacilityController {

    private final FacilityService facilityService;

    public FacilityController(FacilityService facilityService) {
        this.facilityService = facilityService;
    }

    /**
     * 시설명(정확히 일치)으로 보유 화학물질 목록 조회.
     * 예: GET /api/facilities/(주)LG생활건강/substances
     */
    @GetMapping("/api/facilities/{name}/substances")
    public List<FacilityRecord> getSubstances(@PathVariable String name) {
        return facilityService.findByFacilityName(name);
    }

    /**
     * 시설명 일부만으로 검색. 예: GET /api/facilities/search?keyword=효성
     */
    @GetMapping("/api/facilities/search")
    public List<FacilityRecord> search(@RequestParam String keyword) {
        return facilityService.searchByFacilityNameContains(keyword);
    }
}
