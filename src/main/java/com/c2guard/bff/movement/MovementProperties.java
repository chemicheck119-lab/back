package com.c2guard.bff.movement;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("chemicheck119.movement")
public class MovementProperties {

    @Min(3)
    @Max(60)
    private int nextRefreshSeconds = 5;

    private boolean allowDemoSimulation;

    public int getNextRefreshSeconds() {
        return nextRefreshSeconds;
    }

    public void setNextRefreshSeconds(int nextRefreshSeconds) {
        this.nextRefreshSeconds = nextRefreshSeconds;
    }

    public boolean isAllowDemoSimulation() {
        return allowDemoSimulation;
    }

    public void setAllowDemoSimulation(boolean allowDemoSimulation) {
        this.allowDemoSimulation = allowDemoSimulation;
    }
}
