package com.c2guard.bff.phone;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("chemicheck119.phone-ingress")
public class PhoneIngressProperties {

    private boolean enabled;
    private String token = "";
    private int maxTextLength = 4000;
    private Duration claimMaxAge = Duration.ofMinutes(15);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        // Match gateway configuration normalization. Secret text may contain a
        // trailing CR/LF, which cannot be sent in an HTTP header. Request tokens
        // are still compared exactly and an empty configured token fails closed.
        this.token = token == null ? "" : token.trim();
    }

    public int getMaxTextLength() {
        return maxTextLength;
    }

    public void setMaxTextLength(int maxTextLength) {
        this.maxTextLength = maxTextLength;
    }

    public Duration getClaimMaxAge() {
        return claimMaxAge;
    }

    public void setClaimMaxAge(Duration claimMaxAge) {
        this.claimMaxAge = claimMaxAge;
    }
}
