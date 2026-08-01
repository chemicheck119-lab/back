package com.c2guard.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class BffSecurityHealthIndicatorTest {

    @Test
    void readinessFailsClosedWithoutASigningSecret() {
        BffSecurityProperties properties = new BffSecurityProperties();

        assertEquals(Status.DOWN, new BffSecurityHealthIndicator(properties)
                .health().getStatus());
    }

    @Test
    void readinessDoesNotExposeTheSigningSecret() {
        BffSecurityProperties properties = new BffSecurityProperties();
        properties.setSessionSecret("test-only-session-secret-at-least-32-bytes-long");

        var health = new BffSecurityHealthIndicator(properties).health();
        assertEquals(Status.UP, health.getStatus());
        assertFalse(health.getDetails().toString().contains("test-only-session-secret"));
    }

    @Test
    void readinessRejectsAnInsecureCrossSiteCookie() {
        BffSecurityProperties properties = new BffSecurityProperties();
        properties.setSessionSecret("test-only-session-secret-at-least-32-bytes-long");
        properties.setCookieSameSite("None");
        properties.setCookieSecure(false);

        assertEquals(Status.DOWN, new BffSecurityHealthIndicator(properties)
                .health().getStatus());
    }
}
