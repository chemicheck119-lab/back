package com.c2guard.dto;

import java.util.List;

public class IncidentCheckResponse {

    private String facilityName;
    private String incidentSubstance;
    private int heldSubstanceCount;      // 시설이 실제 보유한 걸로 조회된 물질 종류 수
    private boolean facilityFound;       // 시설명 자체가 데이터에 있었는지
    private boolean hasIncompatible;     // 보유물질 중 하나라도 INCOMPATIBLE 있으면 true - 화면에서 빨간 배너 띄울 때 씀
    private List<CompatibilityResponse> results;

    public IncidentCheckResponse(String facilityName, String incidentSubstance, int heldSubstanceCount,
                                  boolean facilityFound, boolean hasIncompatible,
                                  List<CompatibilityResponse> results) {
        this.facilityName = facilityName;
        this.incidentSubstance = incidentSubstance;
        this.heldSubstanceCount = heldSubstanceCount;
        this.facilityFound = facilityFound;
        this.hasIncompatible = hasIncompatible;
        this.results = results;
    }

    public String getFacilityName() {
        return facilityName;
    }

    public String getIncidentSubstance() {
        return incidentSubstance;
    }

    public int getHeldSubstanceCount() {
        return heldSubstanceCount;
    }

    public boolean isFacilityFound() {
        return facilityFound;
    }

    public boolean isHasIncompatible() {
        return hasIncompatible;
    }

    public List<CompatibilityResponse> getResults() {
        return results;
    }
}
