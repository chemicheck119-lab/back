package com.c2guard.auth.staging;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("stagingAuth")
public class StagingAuthHealthIndicator implements HealthIndicator {

    private final StagingAuthProperties properties;

    public StagingAuthHealthIndicator(StagingAuthProperties properties) {
        this.properties = properties;
    }

    @Override
    public Health health() {
        if (!properties.isEnabled()) {
            return Health.up().withDetail("mode", "disabled").build();
        }
        if (!properties.isReady()) {
            return Health.down().withDetail("reason",
                    "staging auth configuration is incomplete").build();
        }
        return Health.up()
                .withDetail("mode", "staging-test-account")
                .withDetail("callbackHost", properties.callbackUri().getHost())
                .build();
    }
}
