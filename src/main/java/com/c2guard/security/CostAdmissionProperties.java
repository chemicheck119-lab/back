package com.c2guard.security;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("chemicheck119.cost-admission")
public class CostAdmissionProperties {
    private boolean enabled = true;
    @Min(1) @Max(64) private int maxInFlight = 2;
    @Min(1) @Max(10000) private int perPrincipalPerMinute = 30;
    @Min(1) @Max(100000) private int globalPerMinute = 120;
    @Min(1) @Max(10000) private int maxPrincipals = 1024;
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public int getMaxInFlight() { return maxInFlight; }
    public void setMaxInFlight(int value) { maxInFlight = value; }
    public int getPerPrincipalPerMinute() { return perPrincipalPerMinute; }
    public void setPerPrincipalPerMinute(int value) { perPrincipalPerMinute = value; }
    public int getGlobalPerMinute() { return globalPerMinute; }
    public void setGlobalPerMinute(int value) { globalPerMinute = value; }
    public int getMaxPrincipals() { return maxPrincipals; }
    public void setMaxPrincipals(int value) { maxPrincipals = value; }
}
