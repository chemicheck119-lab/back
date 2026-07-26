package com.c2guard.dto;

public class IncidentRecordRequest {

    private String facilityName;
    private String incidentSubstance;
    private int heldSubstanceCount;
    private boolean hasIncompatible;
    private String station;

    public IncidentRecordRequest() {
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

    public int getHeldSubstanceCount() {
        return heldSubstanceCount;
    }

    public void setHeldSubstanceCount(int heldSubstanceCount) {
        this.heldSubstanceCount = heldSubstanceCount;
    }

    public boolean isHasIncompatible() {
        return hasIncompatible;
    }

    public void setHasIncompatible(boolean hasIncompatible) {
        this.hasIncompatible = hasIncompatible;
    }

    public String getStation() {
        return station;
    }

    public void setStation(String station) {
        this.station = station;
    }
}