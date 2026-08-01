package com.c2guard.integration.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.http.HttpClient;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModelApiHealthIndicatorTest {

    private MockWebServer server;
    private ModelApiProperties properties;
    private ModelApiClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        properties = new ModelApiProperties();
        properties.setBaseUrl(server.url("/").uri());
        properties.setApiKey("health-test-key");

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getResponseTimeout());
        RestClient restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl().toString())
                .requestFactory(requestFactory)
                .build();
        client = new RestModelApiClient(restClient, new ObjectMapper(), properties, millis -> { });
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void missingSecretMakesReadinessFailClosedWithoutCallingModelApi() {
        properties.setApiKey("");

        Health health = new ModelApiHealthIndicator(client, properties).health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("MODEL_API_KEY_NOT_CONFIGURED", health.getDetails().get("code"));
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void readyModelApiAndConfiguredSecretMakeReadinessUp() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"status\":\"ready\"}"));

        Health health = new ModelApiHealthIndicator(client, properties).health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals("chemiguard119-api-v1", health.getDetails().get("schema"));
        assertEquals(1, server.getRequestCount());
    }

    @Test
    void upstreamFailureExposesOnlyStructuredCodeAndRetryability() {
        server.enqueue(new MockResponse()
                .setResponseCode(503)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"request_id\":\"REQ-HEALTH\",\"error\":{\"code\":\"MODEL_NOT_READY\",\"message\":\"not ready\",\"retryable\":false}}"));

        Health health = new ModelApiHealthIndicator(client, properties).health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("MODEL_NOT_READY", health.getDetails().get("code"));
        assertEquals(false, health.getDetails().get("retryable"));
    }
}
