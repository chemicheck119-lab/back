package com.c2guard.security;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("bffSecurity")
public class BffSecurityHealthIndicator implements HealthIndicator {

    private final BffSecurityProperties properties;

    public BffSecurityHealthIndicator(BffSecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    public Health health() {
        if (!properties.hasValidSessionSecret()) {
            return Health.down()
                    .withDetail("reason", "session signing secret is not configured")
                    .build();
        }
        if (!properties.hasSafeCookiePolicy()) {
            return Health.down()
                    .withDetail("reason", "SameSite=None requires a Secure cookie")
                    .build();
        }
        return Health.up()
                .withDetail("session", "signed-cookie")
                .withDetail("cookieName", properties.getCookieName())
                .build();
    }
}
