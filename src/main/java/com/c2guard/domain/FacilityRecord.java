package com.c2guard.domain;

/**
 * 화학물질종합정보시스템 크롤링 결과 한 행.
 * ulsan_sample_company_chemical_db.csv 한 행에 대응한다.
 * (지금은 염산·황산·수산화나트륨 3종만 샘플로 크롤링된 상태 - 전체 크롤링 미완료)
 */
public class FacilityRecord {

    private final int reportYear;
    private final String facilityId;     // bplcId
    private final String facilityName;   // bplcNm
    private final String address;        // locplcAdres
    private final String industry;       // induty
    private final String substanceName;  // matterNm
    private final String cas;            // casNo
    private final String province;       // ctprvnCdRmate
    private final String district;       // signguCdRmate

    public FacilityRecord(int reportYear, String facilityId, String facilityName, String address,
                           String industry, String substanceName, String cas,
                           String province, String district) {
        this.reportYear = reportYear;
        this.facilityId = facilityId;
        this.facilityName = facilityName;
        this.address = address;
        this.industry = industry;
        this.substanceName = substanceName;
        this.cas = cas;
        this.province = province;
        this.district = district;
    }

    public int getReportYear() {
        return reportYear;
    }

    public String getFacilityId() {
        return facilityId;
    }

    public String getFacilityName() {
        return facilityName;
    }

    public String getAddress() {
        return address;
    }

    public String getIndustry() {
        return industry;
    }

    public String getSubstanceName() {
        return substanceName;
    }

    public String getCas() {
        return cas;
    }

    public String getProvince() {
        return province;
    }

    public String getDistrict() {
        return district;
    }
}
