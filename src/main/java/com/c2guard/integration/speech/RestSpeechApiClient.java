package com.c2guard.integration.speech;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

final class RestSpeechApiClient implements SpeechApiClient {

    static final String API_KEY_HEADER = "X-API-Key";
    static final String REQUEST_ID_HEADER = "X-Request-Id";
    static final String TRANSCRIPTION_PATH = "/api/v1/transcriptions";

    private static final Logger log = LoggerFactory.getLogger(RestSpeechApiClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final SpeechApiProperties properties;
    private final MeterRegistry meterRegistry;
    private final SpeechApiIdentityTokenProvider identityTokenProvider;

    RestSpeechApiClient(RestClient restClient, ObjectMapper objectMapper,
                        SpeechApiProperties properties, MeterRegistry meterRegistry,
                        SpeechApiIdentityTokenProvider identityTokenProvider) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.identityTokenProvider = identityTokenProvider;
    }

    @Override
    public JsonNode transcribe(byte[] audio, MediaType mediaType, String requestedRequestId) {
        String requestId = normalizeRequestId(requestedRequestId);
        if (audio == null || audio.length == 0 || audio.length > properties.getMaxAudioBytes()) {
            throw failure(SpeechApiErrorKind.CONTRACT, "SPEECH_AUDIO_SIZE_INVALID",
                    "음성 크기가 허용 범위를 벗어났습니다.", false, null, requestId, null);
        }
        if (mediaType == null || !isSupportedWav(mediaType)) {
            throw failure(SpeechApiErrorKind.CONTRACT, "SPEECH_MEDIA_TYPE_INVALID",
                    "지원하지 않는 음성 형식입니다.", false, null, requestId, null);
        }
        if (!properties.hasApiKey()) {
            throw failure(SpeechApiErrorKind.CONFIGURATION, "SPEECH_API_KEY_NOT_CONFIGURED",
                    "Speech API Key가 설정되지 않았습니다.", false, null, requestId, null);
        }

        long startedNanos = System.nanoTime();
        try {
            RestClient.RequestBodySpec request = restClient.post()
                    .uri(TRANSCRIPTION_PATH)
                    .header(REQUEST_ID_HEADER, requestId)
                    .header(API_KEY_HEADER, properties.getApiKey())
                    .contentType(mediaType)
                    .accept(MediaType.APPLICATION_JSON);
            if (properties.isIamAuthenticationEnabled()) {
                request.header(HttpHeaders.AUTHORIZATION,
                        identityTokenProvider.authorizationHeader());
            }
            JsonNode response = request.body(audio).exchange((ignored, upstream) ->
                    handleResponse(upstream, requestId));
            validateEnvelope(response, requestId);
            record("SUCCESS", null, System.nanoTime() - startedNanos);
            log.info("speech_api_call operation=transcriptions requestId={} status=success durationMs={}",
                    requestId, elapsedMillis(startedNanos));
            return response;
        } catch (IOException error) {
            SpeechApiException mapped = failure(SpeechApiErrorKind.AUTHENTICATION,
                    "SPEECH_IAM_TOKEN_FAILED",
                    "Speech 서비스 인증 토큰을 발급할 수 없습니다.", false,
                    null, requestId, error);
            recordFailure(mapped, startedNanos);
            throw mapped;
        } catch (ResourceAccessException error) {
            SpeechApiException mapped = mapResourceAccess(error, requestId);
            recordFailure(mapped, startedNanos);
            throw mapped;
        } catch (SpeechApiException error) {
            recordFailure(error, startedNanos);
            throw error;
        }
    }

    private JsonNode handleResponse(ClientHttpResponse response, String requestId)
            throws IOException {
        int status = response.getStatusCode().value();
        JsonNode body = readBounded(response.getBody(), requestId,
                status >= 200 && status < 300);
        if (status >= 200 && status < 300) {
            return body;
        }
        throw mapHttpError(status, body, requestId);
    }

    private JsonNode readBounded(InputStream input, String requestId, boolean requireJson)
            throws IOException {
        byte[] bytes = input.readNBytes(properties.getMaxResponseBytes() + 1);
        if (bytes.length > properties.getMaxResponseBytes()) {
            throw failure(SpeechApiErrorKind.CONTRACT, "SPEECH_RESPONSE_TOO_LARGE",
                    "Speech API 응답이 허용 범위를 초과했습니다.", false,
                    502, requestId, null);
        }
        if (bytes.length == 0) {
            return JsonNodeFactory.instance.objectNode();
        }
        try {
            return objectMapper.readTree(bytes);
        } catch (IOException error) {
            if (!requireJson) {
                return JsonNodeFactory.instance.objectNode();
            }
            throw failure(SpeechApiErrorKind.CONTRACT, "SPEECH_RESPONSE_INVALID_JSON",
                    "Speech API 응답이 JSON이 아닙니다.", false,
                    502, requestId, error);
        }
    }

    private void validateEnvelope(JsonNode body, String requestId) {
        if (!properties.getSchema().equals(body.path("schema_version").asText())) {
            throw failure(SpeechApiErrorKind.CONTRACT, "SPEECH_SCHEMA_MISMATCH",
                    "Speech API schema version이 BE 설정과 일치하지 않습니다.", false,
                    422, requestId, null);
        }
        if (!requestId.equals(body.path("request_id").asText())) {
            throw failure(SpeechApiErrorKind.CONTRACT, "SPEECH_REQUEST_ID_MISMATCH",
                    "Speech API request ID가 BE 요청과 일치하지 않습니다.", false,
                    422, requestId, null);
        }
    }

    private SpeechApiException mapHttpError(int status, JsonNode body, String requestId) {
        JsonNode detail = body.path("error");
        String code = textOrDefault(detail.path("code"), "SPEECH_HTTP_" + status);
        String message = textOrDefault(detail.path("message"),
                "Speech API가 HTTP " + status + "를 반환했습니다.");
        boolean retryable = detail.path("retryable").asBoolean(status == 429);
        SpeechApiErrorKind kind = switch (status) {
            case 401 -> SpeechApiErrorKind.AUTHENTICATION;
            case 400, 413, 415, 422 -> SpeechApiErrorKind.CONTRACT;
            case 429 -> SpeechApiErrorKind.BUSY;
            default -> SpeechApiErrorKind.UPSTREAM;
        };
        if (kind == SpeechApiErrorKind.AUTHENTICATION
                || kind == SpeechApiErrorKind.CONTRACT || status == 500 || status == 502) {
            retryable = false;
        }
        return failure(kind, code, message, retryable, status, requestId, null);
    }

    private SpeechApiException mapResourceAccess(ResourceAccessException error,
                                                  String requestId) {
        if (hasCause(error, SocketTimeoutException.class)
                || hasCause(error, HttpTimeoutException.class)
                || hasTimeoutMessage(error)) {
            return failure(SpeechApiErrorKind.TIMEOUT, "SPEECH_TIMEOUT",
                    "Speech 서비스가 제한 시간 안에 응답하지 않았습니다.", true,
                    null, requestId, error);
        }
        if (hasCause(error, ConnectException.class) || hasCause(error, UnknownHostException.class)) {
            return failure(SpeechApiErrorKind.NETWORK, "SPEECH_CONNECTION_FAILED",
                    "Speech 서비스에 연결할 수 없습니다.", true,
                    null, requestId, error);
        }
        return failure(SpeechApiErrorKind.NETWORK, "SPEECH_NETWORK_ERROR",
                "Speech 서비스 통신에 실패했습니다.", true,
                null, requestId, error);
    }

    private void recordFailure(SpeechApiException error, long startedNanos) {
        record("FAILURE", error.getKind().name(), System.nanoTime() - startedNanos);
        log.warn("speech_api_call operation=transcriptions requestId={} status=failed kind={} code={} upstreamStatus={} retryable={} durationMs={}",
                error.getRequestId(), error.getKind(), error.getCode(), error.getUpstreamStatus(),
                error.isRetryable(), elapsedMillis(startedNanos));
    }

    private void record(String outcome, String kind, long durationNanos) {
        Timer.builder("chemicheck119.speech.api.duration")
                .tags("operation", "transcriptions", "outcome", outcome)
                .register(meterRegistry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
        if (kind != null) {
            Counter.builder("chemicheck119.speech.api.errors")
                    .tags("operation", "transcriptions", "kind", kind)
                    .register(meterRegistry)
                    .increment();
        }
    }

    private long elapsedMillis(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }

    private boolean isSupportedWav(MediaType mediaType) {
        String value = mediaType.getType() + "/" + mediaType.getSubtype();
        return value.equals("audio/wav") || value.equals("audio/x-wav")
                || value.equals("audio/wave");
    }

    private static String normalizeRequestId(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return "REQ-BE-" + UUID.randomUUID();
        }
        if (requestId.length() > 128 || !requestId.matches("^[A-Za-z0-9_.:-]+$")) {
            throw new IllegalArgumentException("requestId is invalid");
        }
        return requestId;
    }

    private static String textOrDefault(JsonNode node, String fallback) {
        return node.isTextual() && !node.asText().isBlank() ? node.asText() : fallback;
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasTimeoutMessage(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase();
                if (normalized.contains("timed out") || normalized.contains("timeout")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static SpeechApiException failure(SpeechApiErrorKind kind, String code,
                                              String message, boolean retryable,
                                              Integer upstreamStatus, String requestId,
                                              Throwable cause) {
        return new SpeechApiException(kind, code, message, retryable,
                upstreamStatus, requestId, cause);
    }
}
