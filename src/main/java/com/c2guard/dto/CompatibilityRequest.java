package com.c2guard.dto;

/**
 * POST /api/compatibility/check 요청 본문.
 * 예: {"substanceA": "황산", "substanceB": "메탄올"}
 */
public class CompatibilityRequest {

    private String substanceA;
    private String substanceB;

    public CompatibilityRequest() {
    }

    public CompatibilityRequest(String substanceA, String substanceB) {
        this.substanceA = substanceA;
        this.substanceB = substanceB;
    }

    public String getSubstanceA() {
        return substanceA;
    }

    public void setSubstanceA(String substanceA) {
        this.substanceA = substanceA;
    }

    public String getSubstanceB() {
        return substanceB;
    }

    public void setSubstanceB(String substanceB) {
        this.substanceB = substanceB;
    }
}
