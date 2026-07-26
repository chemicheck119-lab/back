package com.c2guard.dto;

public class IncidentCheckRequest {

    private String facilityName;
    private String incidentSubstance;

    public IncidentCheckRequest() {
    }

    public String getFacilityName() {
        return facilityName;
    }

    public void setFacilityName(String facilityName) {
        this.facilityName = facilityName;
    }

    public String getIncidentSubstance() {
        return incidentSubstance;
    }

    public void setIncidentSubstance(String incidentSubstance) {
        this.incidentSubstance = incidentSubstance;
    }
}
