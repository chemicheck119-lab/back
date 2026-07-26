package com.c2guard.domain;

/**
 * 화학물정보 x 재난별화학물질 조인 결과 한 건.
 * substance_registry_draft.csv 한 행에 대응한다.
 */
public class Substance {

    private final String koreanName;
    private final String englishName;
    private final String cas;
    private final String unNumber;
    private final String state;
    private final int occurrenceCount;
    private String hazardClass; // 위험물법 유별(1~6류). 아직 대부분 비어있음 - MSDS/CAMEO 연동 후 채워짐

    public Substance(String koreanName, String englishName, String cas,
                      String unNumber, String state, int occurrenceCount, String hazardClass) {
        this.koreanName = koreanName;
        this.englishName = englishName;
        this.cas = cas;
        this.unNumber = unNumber;
        this.state = state;
        this.occurrenceCount = occurrenceCount;
        this.hazardClass = hazardClass;
    }

    public String getKoreanName() {
        return koreanName;
    }

    public String getEnglishName() {
        return englishName;
    }

    public String getCas() {
        return cas;
    }

    public String getUnNumber() {
        return unNumber;
    }

    public String getState() {
        return state;
    }

    public int getOccurrenceCount() {
        return occurrenceCount;
    }

    public String getHazardClass() {
        return hazardClass;
    }

    public void setHazardClass(String hazardClass) {
        this.hazardClass = hazardClass;
    }

    public boolean hasHazardClass() {
        return hazardClass != null && !hazardClass.isBlank();
    }
}
