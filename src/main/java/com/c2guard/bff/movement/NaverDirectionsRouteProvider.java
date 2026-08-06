package com.c2guard.bff.movement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

final class NaverDirectionsRouteProvider implements RouteProvider {

    static final String CLIENT_ID_HEADER = "x-ncp-apigw-api-key-id";
    static final String CLIENT_SECRET_HEADER = "x-ncp-apigw-api-key";
    static final String DIRECTIONS_PATH = "/map-direction/v1/driving";
    static final String PROVIDER = "NAVER_DIRECTIONS_5";
    static final String ATTRIBUTION = "NAVER Maps Directions 5";

    private static final Logger log = LoggerFactory.getLogger(NaverDirectionsRouteProvider.class);
    private static final String ROUTE_OPTION = "traoptimal";
    private static final int MAX_COORDINATE_COUNT = 10_000;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final NaverDirectionsProperties properties;

    NaverDirectionsRouteProvider(RestClient restClient, ObjectMapper objectMapper,
                                 NaverDirectionsProperties properties) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public RouteResult findRoute(RouteRequest request) {
        if (!properties.isEnabled() || !properties.isConfigurationValid()) {
            return unavailable("서버 길찾기 설정이 완료되지 않았습니다.", false);
        }

        long startedNanos = System.nanoTime();
        try {
            RouteResult result = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(DIRECTIONS_PATH)
                            .queryParam("start", coordinate(request.origin()))
                            .queryParam("goal", coordinate(request.destination()))
                            .queryParam("option", ROUTE_OPTION)
                            .queryParam("lang", "ko")
                            .build())
                    .header(CLIENT_ID_HEADER, properties.getClientId())
                    .header(CLIENT_SECRET_HEADER, properties.getClientSecret())
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange((httpRequest, response) -> {
                        int status = response.getStatusCode().value();
                        if (status < 200 || status >= 300) {
                            return unavailableForHttpStatus(status);
                        }
                        return parse(response.getBody(), request.requestedAt());
                    });
            logResult(result, startedNanos);
            return result;
        } catch (ResourceAccessException error) {
            logFailure("network", startedNanos);
            return unavailable("길찾기 서비스에 일시적으로 연결할 수 없습니다.", true);
        } catch (RestClientException error) {
            logFailure("client", startedNanos);
            return unavailable("길찾기 서비스 호출에 실패했습니다.", true);
        }
    }

    private RouteResult parse(java.io.InputStream body, OffsetDateTime requestedAt) {
        try {
            JsonNode root = objectMapper.readTree(body);
            int code = root.path("code").asInt(-1);
            if (code != 0) {
                boolean retryable = code == 3;
                return unavailable("현재 위치와 사고 위치 사이의 도로 경로를 찾지 못했습니다.",
                        retryable);
            }

            JsonNode route = root.path("route").path(ROUTE_OPTION).path(0);
            JsonNode summary = route.path("summary");
            JsonNode path = route.path("path");
            int distanceM = summary.path("distance").asInt(-1);
            long durationMillis = summary.path("duration").asLong(-1L);
            List<List<Double>> coordinates = coordinates(path);
            if (distanceM <= 0 || durationMillis <= 0 || coordinates.size() < 2
                    || coordinates.size() > MAX_COORDINATE_COUNT) {
                return unavailable("길찾기 서비스 응답이 지도 계약을 충족하지 않습니다.", false);
            }

            long durationSecondsLong = Math.max(1L, (durationMillis + 999L) / 1_000L);
            if (durationSecondsLong > Integer.MAX_VALUE) {
                return unavailable("길찾기 서비스 응답 시간이 허용 범위를 벗어났습니다.", false);
            }
            int durationSeconds = (int) durationSecondsLong;
            ServerRoute serverRoute = new ServerRoute(
                    PROVIDER,
                    MovementUpdateResponse.ProviderMode.LIVE_API,
                    "NAVER-" + UUID.randomUUID(),
                    coordinates,
                    distanceM,
                    durationSeconds,
                    distanceM,
                    durationSeconds,
                    requestedAt,
                    true,
                    ATTRIBUTION);
            return new AvailableRoute(serverRoute);
        } catch (IOException | RuntimeException error) {
            return unavailable("길찾기 서비스 응답을 해석하지 못했습니다.", false);
        }
    }

    private List<List<Double>> coordinates(JsonNode path) {
        if (!path.isArray()) {
            return List.of();
        }
        List<List<Double>> result = new ArrayList<>();
        for (JsonNode point : path) {
            if (!point.isArray() || point.size() != 2
                    || !point.get(0).isNumber() || !point.get(1).isNumber()) {
                return List.of();
            }
            double longitude = point.get(0).asDouble();
            double latitude = point.get(1).asDouble();
            if (!Double.isFinite(longitude) || longitude < -180 || longitude > 180
                    || !Double.isFinite(latitude) || latitude < -90 || latitude > 90) {
                return List.of();
            }
            result.add(List.of(longitude, latitude));
            if (result.size() > MAX_COORDINATE_COUNT) {
                return result;
            }
        }
        return List.copyOf(result);
    }

    private RouteResult unavailableForHttpStatus(int status) {
        if (status == 401 || status == 403) {
            return unavailable("길찾기 서비스 인증 설정을 확인해야 합니다.", false);
        }
        if (status == 429 || status >= 500) {
            return unavailable("길찾기 서비스가 일시적으로 요청을 처리할 수 없습니다.", true);
        }
        return unavailable("길찾기 서비스가 요청을 거부했습니다.", false);
    }

    private UnavailableRoute unavailable(String reason, boolean retryable) {
        return new UnavailableRoute(reason, retryable);
    }

    private String coordinate(RoutePoint point) {
        return String.format(Locale.ROOT, "%.7f,%.7f", point.longitude(), point.latitude());
    }

    private void logResult(RouteResult result, long startedNanos) {
        String outcome = result instanceof AvailableRoute ? "success" : "unavailable";
        log.info("route_provider_call provider={} outcome={} durationMs={}", PROVIDER, outcome,
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos));
    }

    private void logFailure(String kind, long startedNanos) {
        log.warn("route_provider_call provider={} outcome=failed kind={} durationMs={}",
                PROVIDER, kind,
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos));
    }
}
