package com.c2guard.integration.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestModelApiClientTest {

    private static final String API_KEY = "test-model-api-key-never-log";
    private static final String REQUEST_ID = "REQ-TEST-0001";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockWebServer server;
    private ModelApiProperties properties;
    private ModelApiClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        properties = properties(server.url("/").uri());
        client = client(properties, millis -> { });
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void exposesEveryModelApiPathWithCorrectAuthenticationBoundary() throws InterruptedException {
        for (int i = 0; i < 10; i++) {
            String body = "{}";
            if (i == 4 || i == 8) {
                body = "{\"schema_version\":\"chemiguard119-api-v1\"}";
            } else if (i == 9) {
                body = "{\"schema_version\":\"chemicheck119-incident-agent-v1\"}";
            }
            server.enqueue(okJson(body));
        }

        JsonNode request = objectMapper.createObjectNode().put("query", "염산");
        client.live(REQUEST_ID);
        client.ready(REQUEST_ID);
        client.metadata(REQUEST_ID);
        client.resolveSubstances(request, REQUEST_ID);
        client.discoverSubstances(request, REQUEST_ID);
        client.searchEvidence(request, REQUEST_ID);
        client.findFacilityCandidates(request, REQUEST_ID);
        client.reviewConflicts(request, REQUEST_ID);
        client.analyzeIncident(request, REQUEST_ID);
        client.stepIncidentAgent(request, REQUEST_ID);

        List<String> expectedPaths = List.of(
                "/health/live",
                "/health/ready",
                "/api/v1/meta",
                "/api/v1/substances/resolve",
                "/api/v1/substances/discover",
                "/api/v1/evidence/search",
                "/api/v1/facilities/candidates",
                "/api/v1/conflicts/review",
                "/api/v1/incidents/analyze",
                "/api/v1/agents/incidents/step"
        );
        for (int i = 0; i < expectedPaths.size(); i++) {
            RecordedRequest recorded = server.takeRequest();
            assertEquals(expectedPaths.get(i), recorded.getPath());
            assertEquals(REQUEST_ID, recorded.getHeader(RestModelApiClient.REQUEST_ID_HEADER));
            if (i < 3) {
                assertEquals(null, recorded.getHeader(RestModelApiClient.API_KEY_HEADER));
            } else {
                assertEquals(API_KEY, recorded.getHeader(RestModelApiClient.API_KEY_HEADER));
            }
        }
    }

    @Test
    void protectedCallPropagatesBodyApiKeyAndRequestId() throws Exception {
        server.enqueue(okJson("{\"schema_version\":\"chemicheck119-incident-agent-v1\",\"request_id\":\"REQ-TEST-0001\"}"));
        ObjectNode body = objectMapper.createObjectNode().put("input", "민감 신고 원문");

        ModelApiResponse response = client.stepIncidentAgent(body, REQUEST_ID);

        RecordedRequest recorded = server.takeRequest();
        assertEquals("POST", recorded.getMethod());
        assertEquals("/api/v1/agents/incidents/step", recorded.getPath());
        assertEquals(API_KEY, recorded.getHeader(RestModelApiClient.API_KEY_HEADER));
        assertEquals(REQUEST_ID, recorded.getHeader(RestModelApiClient.REQUEST_ID_HEADER));
        assertTrue(recorded.getBody().readUtf8().contains("민감 신고 원문"));
        assertEquals(REQUEST_ID, response.requestId());
    }

    @Test
    void retryable503RetriesOnlyOnceAndKeepsRequestId() throws Exception {
        server.enqueue(errorJson(503, "MODEL_NOT_READY", true));
        server.enqueue(okJson("{}"));

        ModelApiResponse response = client.resolveSubstances(query(), REQUEST_ID);

        assertEquals(REQUEST_ID, response.requestId());
        assertEquals(2, server.getRequestCount());
        assertEquals(REQUEST_ID, server.takeRequest().getHeader(RestModelApiClient.REQUEST_ID_HEADER));
        assertEquals(REQUEST_ID, server.takeRequest().getHeader(RestModelApiClient.REQUEST_ID_HEADER));
    }

    @Test
    void nonRetryable503DoesNotRetry() {
        server.enqueue(errorJson(503, "MODEL_NOT_READY", false));

        ModelApiException error = assertThrows(ModelApiException.class,
                () -> client.resolveSubstances(query(), REQUEST_ID));

        assertEquals("MODEL_NOT_READY", error.getCode());
        assertFalse(error.isRetryable());
        assertEquals(1, server.getRequestCount());
    }

    @ParameterizedTest
    @CsvSource({
            "401,MODEL_AUTH_FAILED,AUTHENTICATION",
            "422,MODEL_REQUEST_INVALID,CONTRACT",
            "500,MODEL_OUTPUT_INVALID,UPSTREAM"
    })
    void authenticationContractAndInternalErrorsNeverRetry(int status, String code,
                                                             ModelApiErrorKind kind) {
        server.enqueue(errorJson(status, code, true));

        ModelApiException error = assertThrows(ModelApiException.class,
                () -> client.resolveSubstances(query(), REQUEST_ID));

        assertEquals(code, error.getCode());
        assertEquals(kind, error.getKind());
        assertEquals("safe upstream message", error.getUpstreamMessage());
        assertFalse(error.isRetryable());
        assertEquals(status, error.getUpstreamStatus());
        assertEquals(REQUEST_ID, error.getRequestId());
        assertEquals("2026-08-01T05:30:00+00:00", error.getOccurredAtUtc());
        assertEquals(List.of("body.input"), error.getFields());
        assertEquals(1, server.getRequestCount());
    }

    @Test
    void responseTimeoutIsRetryableButNotAutomaticallyRetried() {
        properties.setResponseTimeout(Duration.ofMillis(100));
        client = client(properties, millis -> { });
        server.enqueue(okJson("{}").setSocketPolicy(SocketPolicy.NO_RESPONSE));

        ModelApiException error = assertThrows(ModelApiException.class,
                () -> client.resolveSubstances(query(), REQUEST_ID));

        assertEquals(ModelApiErrorKind.TIMEOUT, error.getKind());
        assertEquals("MODEL_TIMEOUT", error.getCode());
        assertTrue(error.isRetryable());
        assertFalse(error.allowsAutomaticRetry());
        assertEquals(1, server.getRequestCount());
    }

    @Test
    void connectionFailureIsRetriedOnce() throws IOException {
        AtomicInteger retryWaits = new AtomicInteger();
        ModelApiProperties unreachable = properties(URI.create("http://127.0.0.1:1"));
        unreachable.setConnectTimeout(Duration.ofMillis(100));
        ModelApiClient unreachableClient = client(unreachable, millis -> retryWaits.incrementAndGet());

        ModelApiException error = assertThrows(ModelApiException.class,
                () -> unreachableClient.resolveSubstances(query(), REQUEST_ID));

        assertEquals(ModelApiErrorKind.NETWORK, error.getKind());
        assertEquals("MODEL_CONNECTION_FAILED", error.getCode());
        assertTrue(error.isRetryable());
        assertEquals(1, retryWaits.get());
    }

    @Test
    void missingApiKeyFailsClosedBeforeNetworkCall() {
        properties.setApiKey("");
        client = client(properties, millis -> { });

        ModelApiException error = assertThrows(ModelApiException.class,
                () -> client.resolveSubstances(query(), REQUEST_ID));

        assertEquals(ModelApiErrorKind.CONFIGURATION, error.getKind());
        assertEquals("MODEL_API_KEY_NOT_CONFIGURED", error.getCode());
        assertFalse(error.isRetryable());
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void schemaMismatchFailsAsNonRetryableContractError() {
        server.enqueue(okJson("{\"schema_version\":\"chemiguard119-api-v2\"}"));

        ModelApiException error = assertThrows(ModelApiException.class,
                () -> client.analyzeIncident(query(), REQUEST_ID));

        assertEquals(ModelApiErrorKind.CONTRACT, error.getKind());
        assertEquals("MODEL_SCHEMA_MISMATCH", error.getCode());
        assertFalse(error.isRetryable());
        assertEquals(List.of("schema_version"), error.getFields());
        assertEquals(1, server.getRequestCount());
    }

    @Test
    void incidentAgentUsesItsDedicatedEnvelopeSchema() {
        server.enqueue(okJson("{\"schema_version\":\"chemiguard119-api-v1\"}"));

        ModelApiException error = assertThrows(ModelApiException.class,
                () -> client.stepIncidentAgent(query(), REQUEST_ID));

        assertEquals(ModelApiErrorKind.CONTRACT, error.getKind());
        assertEquals("MODEL_SCHEMA_MISMATCH", error.getCode());
        assertFalse(error.isRetryable());
        assertEquals(1, server.getRequestCount());
    }

    @Test
    void generatedRequestIdIsForwardedAndSecretsAreNotInErrors() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(401)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"error\":{\"code\":\"MODEL_AUTH_FAILED\",\"message\":\"safe upstream message\",\"retryable\":false}}"));

        ModelApiException error = assertThrows(ModelApiException.class,
                () -> client.resolveSubstances(query(), null));

        RecordedRequest recorded = server.takeRequest();
        String generated = recorded.getHeader(RestModelApiClient.REQUEST_ID_HEADER);
        assertNotNull(generated);
        assertTrue(generated.startsWith("REQ-BE-"));
        assertEquals(generated, error.getRequestId());
        assertFalse(error.toString().contains(API_KEY));
        assertFalse(error.toString().contains("염산"));
    }

    private ModelApiClient client(ModelApiProperties clientProperties, RetrySleeper sleeper) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(clientProperties.getConnectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(clientProperties.getResponseTimeout());
        RestClient restClient = RestClient.builder()
                .baseUrl(clientProperties.getBaseUrl().toString())
                .requestFactory(requestFactory)
                .build();
        return new RestModelApiClient(restClient, objectMapper, clientProperties, sleeper);
    }

    private ModelApiProperties properties(URI baseUrl) {
        ModelApiProperties result = new ModelApiProperties();
        result.setBaseUrl(baseUrl);
        result.setApiKey(API_KEY);
        result.setConnectTimeout(Duration.ofSeconds(2));
        result.setResponseTimeout(Duration.ofSeconds(2));
        result.setMaxRetries(1);
        return result;
    }

    private ObjectNode query() {
        return objectMapper.createObjectNode().put("query", "염산");
    }

    private MockResponse okJson(String body) {
        return new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(body);
    }

    private MockResponse errorJson(int status, String code, boolean retryable) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("request_id", REQUEST_ID);
        root.put("occurred_at_utc", "2026-08-01T05:30:00+00:00");
        ObjectNode error = root.putObject("error");
        error.put("code", code);
        error.put("message", "safe upstream message");
        error.put("retryable", retryable);
        error.putArray("fields").add("body.input");
        return new MockResponse()
                .setResponseCode(status)
                .addHeader("Content-Type", "application/json")
                .setBody(root.toString());
    }
}
