package com.c2guard.bff.phone;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("chemicheck119.phone-stream")
public class PhoneStreamProperties {
    private int maxConnections = 32;
    private int maxPerIncident = 4;
    public int getMaxConnections() { return maxConnections; }
    public void setMaxConnections(int value) {
        if (value < 1 || value > 4096) throw new IllegalArgumentException("Invalid SSE connection limit");
        maxConnections = value;
    }
    public int getMaxPerIncident() { return maxPerIncident; }
    public void setMaxPerIncident(int value) {
        if (value < 1 || value > 64) throw new IllegalArgumentException("Invalid SSE incident limit");
        maxPerIncident = value;
    }
}
