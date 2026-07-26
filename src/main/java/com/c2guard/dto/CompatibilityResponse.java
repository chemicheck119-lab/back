package com.c2guard.dto;

import com.c2guard.domain.CompatibilityResult;

/**
 * POST /api/compatibility/check 응답 본문.
 */
public class CompatibilityResponse {

    private String substanceA;
    private String substanceB;
    private CompatibilityResult result;
    private String reason;
    private String basis;

    public CompatibilityResponse(String substanceA, String substanceB,
                                  CompatibilityResult result, String reason, String basis) {
        this.substanceA = substanceA;
        this.substanceB = substanceB;
        this.result = result;
        this.reason = reason;
        this.basis = basis;
    }

    public String getSubstanceA() {
        return substanceA;
    }

    public String getSubstanceB() {
        return substanceB;
    }

    public CompatibilityResult getResult() {
        return result;
    }

    public String getReason() {
        return reason;
    }

    public String getBasis() {
        return basis;
    }
}
