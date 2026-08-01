package com.c2guard.auth.staging;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StagingLoginAttemptLimiterTest {

    @Test
    void blocksAtTheConfiguredFailureLimitAndClearsAfterSuccess() {
        StagingAuthProperties properties = new StagingAuthProperties();
        properties.setMaxFailedAttempts(2);
        StagingLoginAttemptLimiter limiter = new StagingLoginAttemptLimiter(properties,
                Clock.fixed(Instant.parse("2026-08-01T12:00:00Z"), ZoneOffset.UTC));

        limiter.failed("client-hash");
        assertFalse(limiter.blocked("client-hash"));
        limiter.failed("client-hash");
        assertTrue(limiter.blocked("client-hash"));
        limiter.succeeded("client-hash");
        assertFalse(limiter.blocked("client-hash"));
    }
}
