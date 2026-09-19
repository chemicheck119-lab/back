package com.c2guard.bff.phone;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("chemicheck119.phone-ingress")
public class PhoneIngressProperties {

    private boolean enabled;
    private String token = "";
    private int maxTextLength = 4000;

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
        this.token = token;
    }

    public int getMaxTextLength() {
        return maxTextLength;
    }

    public void setMaxTextLength(int maxTextLength) {
        this.maxTextLength = maxTextLength;
    }
}
