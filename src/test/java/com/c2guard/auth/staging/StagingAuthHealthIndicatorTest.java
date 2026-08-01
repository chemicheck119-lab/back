package com.c2guard.auth.staging;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StagingAuthHealthIndicatorTest {

    @Test
    void isUpWhenTheAdapterIsDisabled() {
        StagingAuthProperties properties = new StagingAuthProperties();

        assertEquals(Status.UP,
                new StagingAuthHealthIndicator(properties).health().getStatus());
    }

    @Test
    void isDownWhenEnabledConfigurationIsIncomplete() {
        StagingAuthProperties properties = new StagingAuthProperties();
        properties.setEnabled(true);

        assertEquals(Status.DOWN,
                new StagingAuthHealthIndicator(properties).health().getStatus());
    }

    @Test
    void isUpForACompleteSyntheticAccount() {
        StagingAuthProperties properties = new StagingAuthProperties();
        properties.setEnabled(true);
        properties.setCallbackUrl("https://chemicheck119.site");
        properties.setUserId("responder-staging");
        properties.setStationId("station-seoul-119");
        properties.setStationDisplayName("서울 테스트 소방서");
        properties.setPassword("staging-test-password-at-least-16-bytes");

        assertEquals(Status.UP,
                new StagingAuthHealthIndicator(properties).health().getStatus());
    }
}
