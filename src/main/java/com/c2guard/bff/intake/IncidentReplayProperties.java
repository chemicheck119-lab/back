package com.c2guard.bff.intake;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("chemicheck119.incident-replay")
public class IncidentReplayProperties {

    private boolean enabled;
    private boolean publicEndpointEnabled;
    private boolean syntheticConfirmationEnabled;
    private Duration delay = Duration.ofSeconds(1);
    private Duration timeout = Duration.ofSeconds(10);
    private Duration syntheticIncidentTtl = Duration.ofMinutes(30);
    private int maxActiveSyntheticIncidents = 100;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isPublicEndpointEnabled() {
        return publicEndpointEnabled;
    }

    public void setPublicEndpointEnabled(boolean publicEndpointEnabled) {
        this.publicEndpointEnabled = publicEndpointEnabled;
    }

    public boolean isSyntheticConfirmationEnabled() {
        return syntheticConfirmationEnabled;
    }

    public void setSyntheticConfirmationEnabled(boolean syntheticConfirmationEnabled) {
        this.syntheticConfirmationEnabled = syntheticConfirmationEnabled;
    }

    public Duration getDelay() {
        return delay;
    }

    public void setDelay(Duration delay) {
        this.delay = delay;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public Duration getSyntheticIncidentTtl() {
        return syntheticIncidentTtl;
    }

    public void setSyntheticIncidentTtl(Duration syntheticIncidentTtl) {
        this.syntheticIncidentTtl = syntheticIncidentTtl;
    }

    public int getMaxActiveSyntheticIncidents() {
        return maxActiveSyntheticIncidents;
    }

    public void setMaxActiveSyntheticIncidents(int maxActiveSyntheticIncidents) {
        this.maxActiveSyntheticIncidents = maxActiveSyntheticIncidents;
    }
}
