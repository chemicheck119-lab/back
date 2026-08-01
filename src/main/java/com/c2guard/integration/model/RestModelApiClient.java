package com.c2guard.integration.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

final class RestModelApiClient implements ModelApiClient {

    static final String API_KEY_HEADER = "X-API-Key";
    static final String REQUEST_ID_HEADER = "X-Request-Id";
    static final String INCIDENT_AGENT_SCHEMA = "chemicheck119-incident-agent-v1";

    private static final Logger log = LoggerFactory.getLogger(RestModelApiClient.class);
    private static final int RETRY_JITTER_MIN_MILLIS = 50;
    private static final int RETRY_JITTER_MAX_MILLIS = 150;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ModelApiProperties properties;
    private final RetrySleeper retrySleeper;

    RestModelApiClient(RestClient restClient, ObjectMapper objectMapper,
                       ModelApiProperties properties, RetrySleeper retrySleeper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.retrySleeper = retrySleeper;
    }

    @Override
    public ModelApiResponse live(String requestId) {
        return get("/health/live", requestId);
    }

    @Override
    public ModelApiResponse ready(String requestId) {
        return get("/health/ready", requestId);
    }

    @Override
    public ModelApiResponse metadata(String requestId) {
        return get("/api/v1/meta", requestId);
    }

    @Override
    public ModelApiResponse resolveSubstances(JsonNode request, String requestId) {
        return post("/api/v1/substances/resolve", request, requestId, null);
    }

    @Override
    public ModelApiResponse discoverSubstances(JsonNode request, String requestId) {
        return post("/api/v1/substances/discover", request, requestId,
                properties.getSchema());
    }

    @Override
    public ModelApiResponse searchEvidence(JsonNode request, String requestId) {
        return post("/api/v1/evidence/search", request, requestId, null);
    }

    @Override
    public ModelApiResponse findFacilityCandidates(JsonNode request, String requestId) {
        return post("/api/v1/facilities/candidates", request, requestId, null);
    }

    @Override
    public ModelApiResponse reviewConflicts(JsonNode request, String requestId) {
        return post("/api/v1/conflicts/review", request, requestId, null);
    }

    @Override
    public ModelApiResponse analyzeIncident(JsonNode request, String requestId) {
        return post("/api/v1/incidents/analyze", request, requestId,
                properties.getSchema());
    }

    @Override
    public ModelApiResponse stepIncidentAgent(JsonNode request, String requestId) {
        return post("/api/v1/agents/incidents/step", request, requestId,
                INCIDENT_AGENT_SCHEMA);
    }

    private ModelApiResponse get(String path, String requestId) {
        return execute(HttpMethod.GET, path, null, requestId, false, null);
    }

    private ModelApiResponse post(String path, JsonNode request, String requestId,
                                  String expectedSchemaVersion) {
        if (request == null) {
            throw new IllegalArgumentException("Model API request body must not be null");
        }
        return execute(HttpMethod.POST, path, request, requestId, true,
                expectedSchemaVersion);
    }

    private ModelApiResponse execute(HttpMethod method, String path, JsonNode request,
                                     String requestedRequestId, boolean authenticated,
                                     String expectedSchemaVersion) {
        String requestId = normalizeRequestId(requestedRequestId);
        if (authenticated && !properties.hasApiKey()) {
            throw exception(ModelApiErrorKind.CONFIGURATION, "MODEL_API_KEY_NOT_CONFIGURED",
                    "모델 API Key가 설정되지 않았습니다.", false, null, requestId, List.of(), null);
        }

        int maxAttempts = 1 + properties.getMaxRetries();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            long startedNanos = System.nanoTime();
            try {
                ModelApiResponse response = exchange(method, path, request, requestId, authenticated);
                validateSchema(response.body(), expectedSchemaVersion, requestId);
                log.debug("model_api_call method={} path={} requestId={} status=success durationMs={} attempt={}",
                        method, path, requestId, elapsedMillis(startedNanos), attempt);
                return response;
            } catch (ResourceAccessException error) {
                ModelApiException mapped = mapResourceAccess(error, requestId);
                if (attempt < maxAttempts && mapped.allowsAutomaticRetry()) {
                    waitBeforeRetry(requestId);
                    continue;
                }
                log.warn("model_api_call method={} path={} requestId={} status=failed code={} retryable={} durationMs={} attempts={}",
                        method, path, requestId, mapped.getCode(), mapped.isRetryable(),
                        elapsedMillis(startedNanos), attempt);
                throw mapped;
            } catch (ModelApiException error) {
                if (attempt < maxAttempts && error.allowsAutomaticRetry()) {
                    waitBeforeRetry(requestId);
                    continue;
                }
                log.warn("model_api_call method={} path={} requestId={} status=failed code={} upstreamStatus={} retryable={} durationMs={} attempts={}",
                        method, path, requestId, error.getCode(), error.getUpstreamStatus(),
                        error.isRetryable(), elapsedMillis(startedNanos), attempt);
                throw error;
            }
        }
        throw new IllegalStateException("unreachable");
    }

    private ModelApiResponse exchange(HttpMethod method, String path, JsonNode request,
                                      String requestId, boolean authenticated) {
        RestClient.RequestBodySpec requestSpec = restClient.method(method)
                .uri(path)
                .header(REQUEST_ID_HEADER, requestId)
                .accept(MediaType.APPLICATION_JSON);
        if (authenticated) {
            requestSpec.header(API_KEY_HEADER, properties.getApiKey());
        }

        RestClient.RequestHeadersSpec<?> headersSpec = request == null
                ? requestSpec
                : requestSpec.contentType(MediaType.APPLICATION_JSON).body(request);

        return headersSpec.exchange((httpRequest, httpResponse) -> {
            int status = httpResponse.getStatusCode().value();
            JsonNode body = readBody(httpResponse.getBody());
            if (status >= 200 && status < 300) {
                return new ModelApiResponse(requestId, body);
            }
            throw mapHttpError(status, body, requestId);
        });
    }

    private JsonNode readBody(java.io.InputStream inputStream) throws IOException {
        byte[] bytes = inputStream.readAllBytes();
        return bytes.length == 0 ? JsonNodeFactory.instance.objectNode() : objectMapper.readTree(bytes);
    }

    private ModelApiException mapHttpError(int status, JsonNode body, String fallbackRequestId) {
        JsonNode detail = body.path("error");
        String requestId = textOrDefault(body.path("request_id"), fallbackRequestId);
        String occurredAtUtc = textOrDefault(body.path("occurred_at_utc"), null);
        String upstreamCode = textOrDefault(detail.path("code"), "MODEL_HTTP_" + status);
        String message = textOrDefault(detail.path("message"), "모델 API가 HTTP " + status + "를 반환했습니다.");
        boolean retryable = detail.path("retryable").asBoolean(false);
        List<String> fields = new ArrayList<>();
        detail.path("fields").forEach(field -> fields.add(field.asText()));

        ModelApiErrorKind kind = switch (status) {
            case 401 -> ModelApiErrorKind.AUTHENTICATION;
            case 422 -> ModelApiErrorKind.CONTRACT;
            default -> ModelApiErrorKind.UPSTREAM;
        };
        if (status == 401) {
            retryable = false;
        } else if (status == 422 || status == 500) {
            retryable = false;
        }

        return new ModelApiException(kind, upstreamCode, message, retryable, status,
                requestId, occurredAtUtc, fields, null);
    }

    private ModelApiException mapResourceAccess(ResourceAccessException error, String requestId) {
        if (hasCause(error, SocketTimeoutException.class)
                || hasCause(error, HttpTimeoutException.class)
                || hasTimeoutMessage(error)) {
            return exception(ModelApiErrorKind.TIMEOUT, "MODEL_TIMEOUT",
                    "모델 서비스가 제한 시간 안에 응답하지 않았습니다.", true,
                    null, requestId, List.of(), error);
        }
        if (hasCause(error, ConnectException.class) || hasCause(error, UnknownHostException.class)) {
            return exception(ModelApiErrorKind.NETWORK, "MODEL_CONNECTION_FAILED",
                    "모델 서비스에 연결할 수 없습니다.", true,
                    null, requestId, List.of(), error);
        }
        return exception(ModelApiErrorKind.NETWORK, "MODEL_NETWORK_ERROR",
                "모델 서비스 통신에 실패했습니다.", true,
                null, requestId, List.of(), error);
    }

    private void validateSchema(JsonNode body, String expectedSchemaVersion,
                                String requestId) {
        if (expectedSchemaVersion == null) {
            return;
        }
        String actual = textOrDefault(body.path("schema_version"), "");
        if (!expectedSchemaVersion.equals(actual)) {
            throw exception(ModelApiErrorKind.CONTRACT, "MODEL_SCHEMA_MISMATCH",
                    "모델 API schema version이 BE 설정과 일치하지 않습니다.", false,
                    422, requestId, List.of("schema_version"), null);
        }
    }

    private void waitBeforeRetry(String requestId) {
        long delayMillis = ThreadLocalRandom.current()
                .nextLong(RETRY_JITTER_MIN_MILLIS, RETRY_JITTER_MAX_MILLIS + 1L);
        try {
            retrySleeper.sleep(delayMillis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw exception(ModelApiErrorKind.INTERRUPTED, "MODEL_RETRY_INTERRUPTED",
                    "모델 API 재시도 대기가 중단됐습니다.", false,
                    null, requestId, List.of(), interrupted);
        }
    }

    private static String normalizeRequestId(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return "REQ-BE-" + UUID.randomUUID();
        }
        if (requestId.length() > 128 || !requestId.matches("^[A-Za-z0-9_.:-]+$")) {
            throw new IllegalArgumentException("requestId must match ^[A-Za-z0-9_.:-]+$ and be at most 128 characters");
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

    private static long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }

    private static ModelApiException exception(ModelApiErrorKind kind, String code, String message,
                                                boolean retryable, Integer upstreamStatus,
                                                String requestId, List<String> fields, Throwable cause) {
        return new ModelApiException(kind, code, message, retryable,
                upstreamStatus, requestId, fields, cause);
    }
}
