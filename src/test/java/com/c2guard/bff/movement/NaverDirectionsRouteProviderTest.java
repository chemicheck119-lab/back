package com.c2guard.bff.movement;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NaverDirectionsRouteProviderTest {

    private static final String CLIENT_ID = "test-client-id-never-log";
    private static final String CLIENT_SECRET = "test-client-secret-never-log";
    private static final OffsetDateTime REQUESTED_AT =
            OffsetDateTime.parse("2026-08-06T18:30:00+09:00");

    private MockWebServer server;
    private NaverDirectionsProperties properties;
    private RouteProvider provider;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        properties = properties();
        provider = provider(Duration.ofSeconds(2));
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void convertsDirections5ResponseToLiveGeoJsonContract() throws InterruptedException {
        server.enqueue(json(200, """
                {
                  "code": 0,
                  "route": {
                    "traoptimal": [{
                      "summary": {"distance": 1820, "duration": 245001},
                      "path": [
                        [127.0474000, 37.5173000],
                        [127.0510000, 37.5200000],
                        [127.0562000, 37.5239000]
                      ]
                    }]
                  }
                }
                """));

        RouteProvider.AvailableRoute available = assertInstanceOf(
                RouteProvider.AvailableRoute.class, provider.findRoute(request()));
        RouteProvider.ServerRoute route = available.route();

        assertEquals(NaverDirectionsRouteProvider.PROVIDER, route.provider());
        assertEquals(MovementUpdateResponse.ProviderMode.LIVE_API, route.mode());
        assertTrue(route.routeId().startsWith("NAVER-"));
        assertEquals(List.of(
                List.of(127.0474, 37.5173),
                List.of(127.051, 37.52),
                List.of(127.0562, 37.5239)), route.coordinates());
        assertEquals(1820, route.distanceM());
        assertEquals(246, route.durationSeconds());
        assertEquals(1820, route.remainingDistanceM());
        assertEquals(246, route.remainingDurationSeconds());
        assertEquals(REQUESTED_AT, route.generatedAt());
        assertTrue(route.trafficApplied());
        assertEquals(NaverDirectionsRouteProvider.ATTRIBUTION, route.attribution());

        RecordedRequest recorded = server.takeRequest();
        assertEquals("GET", recorded.getMethod());
        assertEquals(CLIENT_ID,
                recorded.getHeader(NaverDirectionsRouteProvider.CLIENT_ID_HEADER));
        assertEquals(CLIENT_SECRET,
                recorded.getHeader(NaverDirectionsRouteProvider.CLIENT_SECRET_HEADER));
        HttpUrl requestUrl = recorded.getRequestUrl();
        assertEquals(NaverDirectionsRouteProvider.DIRECTIONS_PATH,
                requestUrl.encodedPath());
        assertEquals("127.0474000,37.5173000", requestUrl.queryParameter("start"));
        assertEquals("127.0562000,37.5239000", requestUrl.queryParameter("goal"));
        assertEquals("traoptimal", requestUrl.queryParameter("option"));
        assertEquals("ko", requestUrl.queryParameter("lang"));
    }

    @Test
    void routeNotFoundFailsClosedWithoutGeometry() {
        server.enqueue(json(200, "{\"code\":2,\"message\":\"Route not found\"}"));

        RouteProvider.UnavailableRoute unavailable = assertInstanceOf(
                RouteProvider.UnavailableRoute.class, provider.findRoute(request()));

        assertFalse(unavailable.retryable());
        assertTrue(unavailable.reason().contains("도로 경로"));
    }

    @Test
    void invalidSuccessPayloadFailsClosed() {
        server.enqueue(json(200, "{\"code\":0,\"route\":{\"traoptimal\":[]}}"));

        RouteProvider.UnavailableRoute unavailable = assertInstanceOf(
                RouteProvider.UnavailableRoute.class, provider.findRoute(request()));

        assertFalse(unavailable.retryable());
        assertTrue(unavailable.reason().contains("지도 계약"));
    }

    @Test
    void rateLimitAndServerFailuresAreRetryable() {
        server.enqueue(json(429, "{}"));
        server.enqueue(json(503, "{}"));

        RouteProvider.UnavailableRoute rateLimited = assertInstanceOf(
                RouteProvider.UnavailableRoute.class, provider.findRoute(request()));
        RouteProvider.UnavailableRoute serverFailure = assertInstanceOf(
                RouteProvider.UnavailableRoute.class, provider.findRoute(request()));

        assertTrue(rateLimited.retryable());
        assertTrue(serverFailure.retryable());
    }

    @Test
    void authenticationFailureIsNotRetryableAndDoesNotExposeCredential() {
        server.enqueue(json(401, "{\"error\":\"unauthorized\"}"));

        RouteProvider.UnavailableRoute unavailable = assertInstanceOf(
                RouteProvider.UnavailableRoute.class, provider.findRoute(request()));

        assertFalse(unavailable.retryable());
        assertFalse(unavailable.reason().contains(CLIENT_ID));
        assertFalse(unavailable.reason().contains(CLIENT_SECRET));
    }

    @Test
    void timeoutIsRetryable() {
        provider = provider(Duration.ofMillis(100));
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));

        RouteProvider.UnavailableRoute unavailable = assertInstanceOf(
                RouteProvider.UnavailableRoute.class, provider.findRoute(request()));

        assertTrue(unavailable.retryable());
    }

    private RouteProvider provider(Duration responseTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(1))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(responseTimeout);
        RestClient restClient = RestClient.builder()
                .baseUrl(server.url("/").toString())
                .requestFactory(requestFactory)
                .build();
        return new NaverDirectionsRouteProvider(restClient, new ObjectMapper(), properties);
    }

    private NaverDirectionsProperties properties() {
        NaverDirectionsProperties result = new NaverDirectionsProperties();
        result.setEnabled(true);
        result.setBaseUrl(URI.create("https://maps.apigw.ntruss.com"));
        result.setClientId(CLIENT_ID);
        result.setClientSecret(CLIENT_SECRET);
        result.setConnectTimeout(Duration.ofSeconds(1));
        result.setResponseTimeout(Duration.ofSeconds(2));
        return result;
    }

    private RouteProvider.RouteRequest request() {
        return new RouteProvider.RouteRequest(
                new RouteProvider.RoutePoint(37.5173, 127.0474),
                new RouteProvider.RoutePoint(37.5239, 127.0562),
                REQUESTED_AT);
    }

    private MockResponse json(int status, String body) {
        return new MockResponse()
                .setResponseCode(status)
                .addHeader("Content-Type", "application/json")
                .setBody(body);
    }
}
