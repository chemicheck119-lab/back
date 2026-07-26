package com.c2guard.dto;

import java.time.LocalDateTime;

public class IncidentRecordResponse {

    private long id;
    private String facilityName;
    private String incidentSubstance;
    private int heldSubstanceCount;
    private boolean hasIncompatible;
    private String station;
    private LocalDateTime confirmedAt;

    public IncidentRecordResponse(long id, String facilityName, String incidentSubstance,
                                  int heldSubstanceCount, boolean hasIncompatible,
                                  String station, LocalDateTime confirmedAt) {
        this.id = id;
        this.facilityName = facilityName;
        this.incidentSubstance = incidentSubstance;
        this.heldSubstanceCount = heldSubstanceCount;
        this.hasIncompatible = hasIncompatible;
        this.station = station;
        this.confirmedAt = confirmedAt;
    }

    public long getId() {
        return id;
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

    public boolean isHasIncompatible() {
        return hasIncompatible;
    }

    public String getStation() {
        return station;
    }

    public LocalDateTime getConfirmedAt() {
        return confirmedAt;
    }
}