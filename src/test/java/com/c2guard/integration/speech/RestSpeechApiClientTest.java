package com.c2guard.integration.speech;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestSpeechApiClientTest {

    private static final String API_KEY = "test-speech-key-never-log";
    private static final String REQUEST_ID = "REQ-SPEECH-0001";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockWebServer server;
    private SpeechApiProperties properties;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        meterRegistry = new SimpleMeterRegistry();
        properties = new SpeechApiProperties();
        properties.setBaseUrl(server.url("/").uri());
        properties.setApiKey(API_KEY);
        properties.setConnectTimeout(Duration.ofSeconds(2));
        properties.setResponseTimeout(Duration.ofSeconds(2));
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void forwardsBoundedAudioCredentialsAndSameRequestId() throws Exception {
        server.enqueue(ok(fixture().toString()));
        byte[] audio = wav();

        JsonNode response = client(SpeechApiIdentityTokenProvider.disabled())
                .transcribe(audio, MediaType.parseMediaType("audio/wav"), REQUEST_ID);

        RecordedRequest request = server.takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/api/v1/transcriptions", request.getPath());
        assertEquals(API_KEY, request.getHeader(RestSpeechApiClient.API_KEY_HEADER));
        assertEquals(REQUEST_ID, request.getHeader(RestSpeechApiClient.REQUEST_ID_HEADER));
        assertEquals("audio/wav", request.getHeader("Content-Type"));
        assertArrayEquals(audio, request.getBody().readByteArray());
        assertEquals(REQUEST_ID, response.path("request_id").asText());
    }

    @Test
    void addsIamTokenWhenEnabled() throws Exception {
        properties.setIamAuthenticationEnabled(true);
        server.enqueue(ok(fixture().toString()));

        client(() -> "Bearer test-id-token")
                .transcribe(wav(), MediaType.parseMediaType("audio/wav"), REQUEST_ID);

        assertEquals("Bearer test-id-token",
                server.takeRequest().getHeader("Authorization"));
    }

    @Test
    void missingApiKeyAndIamFailureAreFailClosedBeforeNetwork() {
        properties.setApiKey("");
        SpeechApiException missingKey = assertThrows(SpeechApiException.class,
                () -> client(SpeechApiIdentityTokenProvider.disabled())
                        .transcribe(wav(), MediaType.parseMediaType("audio/wav"), REQUEST_ID));
        assertEquals(SpeechApiErrorKind.CONFIGURATION, missingKey.getKind());
        assertFalse(missingKey.isRetryable());

        properties.setApiKey(API_KEY);
        properties.setIamAuthenticationEnabled(true);
        SpeechApiException iam = assertThrows(SpeechApiException.class,
                () -> client(() -> { throw new IOException("metadata unavailable"); })
                        .transcribe(wav(), MediaType.parseMediaType("audio/wav"), REQUEST_ID));
        assertEquals(SpeechApiErrorKind.AUTHENTICATION, iam.getKind());
        assertEquals("SPEECH_IAM_TOKEN_FAILED", iam.getCode());
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void rejectsSchemaAndRequestIdMismatch() throws IOException {
        JsonNode wrongSchema = fixture().deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) wrongSchema)
                .put("schema_version", "speech-v2");
        server.enqueue(ok(wrongSchema.toString()));
        SpeechApiException schema = assertThrows(SpeechApiException.class,
                () -> client(SpeechApiIdentityTokenProvider.disabled())
                        .transcribe(wav(), MediaType.parseMediaType("audio/wav"), REQUEST_ID));
        assertEquals("SPEECH_SCHEMA_MISMATCH", schema.getCode());

        JsonNode wrongRequestId = fixture().deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) wrongRequestId)
                .put("request_id", "REQ-WRONG");
        server.enqueue(ok(wrongRequestId.toString()));
        SpeechApiException requestId = assertThrows(SpeechApiException.class,
                () -> client(SpeechApiIdentityTokenProvider.disabled())
                        .transcribe(wav(), MediaType.parseMediaType("audio/wav"), REQUEST_ID));
        assertEquals("SPEECH_REQUEST_ID_MISMATCH", requestId.getCode());
    }

    @Test
    void mapsBusyAndRejectsOversizedResponseWithoutRetry() {
        server.enqueue(new MockResponse().setResponseCode(429)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"error\":{\"code\":\"QUEUE_TIMEOUT\",\"message\":\"busy\",\"retryable\":true}}"));
        SpeechApiException busy = assertThrows(SpeechApiException.class,
                () -> client(SpeechApiIdentityTokenProvider.disabled())
                        .transcribe(wav(), MediaType.parseMediaType("audio/wav"), REQUEST_ID));
        assertEquals(SpeechApiErrorKind.BUSY, busy.getKind());
        assertEquals(1, server.getRequestCount());

        properties.setMaxResponseBytes(32);
        server.enqueue(ok("x".repeat(33)));
        SpeechApiException large = assertThrows(SpeechApiException.class,
                () -> client(SpeechApiIdentityTokenProvider.disabled())
                        .transcribe(wav(), MediaType.parseMediaType("audio/wav"), REQUEST_ID));
        assertEquals("SPEECH_RESPONSE_TOO_LARGE", large.getCode());
        assertFalse(large.isRetryable());
        assertEquals(2, server.getRequestCount());
    }

    @Test
    void mapsPlainTextPlatform429ToRetryableBusy() {
        server.enqueue(new MockResponse().setResponseCode(429)
                .addHeader("Content-Type", "text/plain")
                .setBody("The request was aborted because there was no available instance."));

        SpeechApiException busy = assertThrows(SpeechApiException.class,
                () -> client(SpeechApiIdentityTokenProvider.disabled())
                        .transcribe(wav(), MediaType.parseMediaType("audio/wav"), REQUEST_ID));

        assertEquals(SpeechApiErrorKind.BUSY, busy.getKind());
        assertEquals("SPEECH_HTTP_429", busy.getCode());
        assertEquals(429, busy.getUpstreamStatus());
        assertTrue(busy.isRetryable());
        assertEquals(1, server.getRequestCount());
    }

    private SpeechApiClient client(SpeechApiIdentityTokenProvider tokenProvider) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout()).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getResponseTimeout());
        RestClient restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl().toString())
                .requestFactory(requestFactory).build();
        return new RestSpeechApiClient(restClient, objectMapper, properties,
                meterRegistry, tokenProvider);
    }

    private JsonNode fixture() throws IOException {
        return objectMapper.readTree(Files.readString(Path.of(
                "src/test/resources/fixtures/speech/transcription_response.json")));
    }

    private MockResponse ok(String body) {
        return new MockResponse().setResponseCode(200)
                .addHeader("Content-Type", "application/json").setBody(body);
    }

    private byte[] wav() {
        byte[] result = new byte[44];
        System.arraycopy("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                0, result, 0, 4);
        System.arraycopy("WAVE".getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                0, result, 8, 4);
        return result;
    }
}
