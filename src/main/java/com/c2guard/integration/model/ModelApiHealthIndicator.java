package com.c2guard.integration.model;

import com.fasterxml.jackson.databind.JsonNode;
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
            JsonNode metadata = modelApiClient.metadata(requestId).body();
            if (!supportsIncidentAgent(metadata)) {
                return Health.down()
                        .withDetail("code", "MODEL_AGENT_CAPABILITY_NOT_READY")
                        .withDetail("retryable", false)
                        .build();
            }
            return Health.up()
                    .withDetail("schema", properties.getSchema())
                    .withDetail("agentSchema", RestModelApiClient.INCIDENT_AGENT_SCHEMA)
                    .withDetail("agentMemoryMode", "BE_PERSISTED_EXTERNAL_MEMORY")
                    .build();
        } catch (ModelApiException error) {
            return Health.down()
                    .withDetail("code", error.getCode())
                    .withDetail("retryable", error.isRetryable())
                    .build();
        }
    }

    private boolean supportsIncidentAgent(JsonNode metadata) {
        JsonNode capability = metadata.path("incident_agent_capability");
        return properties.getSchema().equals(metadata.path("api_schema_version").asText())
                && RestModelApiClient.INCIDENT_AGENT_SCHEMA.equals(
                capability.path("schema_version").asText())
                && "/api/v1/agents/incidents/step".equals(
                capability.path("endpoint").asText())
                && "BE_PERSISTED_EXTERNAL_MEMORY".equals(
                capability.path("memory_mode").asText())
                && !capability.path("server_side_session_storage").asBoolean(true)
                && !capability.path("memory_can_trigger_rule").asBoolean(true)
                && !capability.path("autonomous_risk_decision_allowed").asBoolean(true);
    }
}
