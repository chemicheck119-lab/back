package com.c2guard.integration.model;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component("modelApi")
public class ModelApiHealthIndicator implements HealthIndicator {

    private final ModelApiClient modelApiClient;
    private final ModelApiProperties properties;

    public ModelApiHealthIndicator(ModelApiClient modelApiClient, ModelApiProperties properties) {
        this.modelApiClient = modelApiClient;
        this.properties = properties;
    }

    @Override
    public Health health() {
        if (!properties.hasApiKey()) {
            return Health.down()
                    .withDetail("code", "MODEL_API_KEY_NOT_CONFIGURED")
                    .withDetail("retryable", false)
                    .build();
        }

        String requestId = "REQ-HEALTH-" + UUID.randomUUID();
        try {
            modelApiClient.ready(requestId);
            return Health.up()
                    .withDetail("schema", properties.getSchema())
                    .build();
        } catch (ModelApiException error) {
            return Health.down()
                    .withDetail("code", error.getCode())
                    .withDetail("retryable", error.isRetryable())
                    .build();
        }
    }
}
