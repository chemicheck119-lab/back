package com.c2guard.domain;

import java.time.LocalDateTime;

/**
 * "대응기록 저장" 1건.
 * 대응충돌검토 화면에서 지휘관이 "확인" 버튼을 눌렀을 때 생성되는 기록.
 * <p>
 * 지금은 DB 없이 서버 메모리(List)에만 쌓인다 - 서버 재시작하면 초기화됨.
 * TODO: JPA/H2 연동되면 이 클래스를 @Entity로 교체하고 IncidentRecordRepository로 전환.
 */
public class IncidentRecord {

    private final long id;
    private final String facilityName;
    private final String incidentSubstance;
    private final int heldSubstanceCount;
    private final boolean hasIncompatible;
    private final String station;          // 확인한 소방서 (로그인 시 입력값)
    private final LocalDateTime confirmedAt;

    public IncidentRecord(long id, String facilityName, String incidentSubstance,
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