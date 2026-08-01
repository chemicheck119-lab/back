package com.c2guard.bff.movement;

import com.c2guard.bff.common.BffContractException;
import com.c2guard.security.BffRole;
import com.c2guard.security.BffUserPrincipal;
import com.c2guard.security.IncidentAccessPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovementServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-01T05:30:00Z");
    private static final String INCIDENT_ID = "INC-MOVEMENT-SERVICE";

    private IncidentMovementContextStore contextStore;
    private MovementStateStore stateStore;
    private BffUserPrincipal principal;

    @BeforeEach
    void setUp() {
        contextStore = new IncidentMovementContextStore();
        stateStore = new MovementStateStore();
        contextStore.save(INCIDENT_ID, new IncidentMovementContextStore.IncidentPosition(
                37.2181, 126.9417, "예시 사업장", "DISPATCH_SYSTEM",
                OffsetDateTime.parse("2026-08-01T14:20:00+09:00"), false),
                "화성소방서");
        principal = new BffUserPrincipal("responder-1", "station-1",
                Set.of(BffRole.RESPONDER), Set.of(INCIDENT_ID), "session-1",
                NOW.minusSeconds(60), NOW.plusSeconds(3600));
    }

    @Test
    void mapsAProviderRouteToGeoJsonWithProvenance() {
        RouteProvider provider = ignored -> new RouteProvider.AvailableRoute(
                validRoute(List.of(
                        List.of(126.8311, 37.2065),
                        List.of(126.9000, 37.2100),
                        List.of(126.9417, 37.2181))));

        MovementUpdateResponse response = service(provider).update(INCIDENT_ID,
                request(17, NOW.minusSeconds(30),
                        MovementUpdateRequest.JourneyState.EN_ROUTE),
                "REQ-MOVE-17", principal);

        assertEquals(MovementUpdateResponse.RouteStatus.AVAILABLE,
                response.mapContext().route().status());
        assertEquals("LineString", response.mapContext().route().geometry().type());
        assertEquals(List.of(126.8311, 37.2065),
                response.mapContext().route().geometry().coordinates().get(0));
        assertEquals(480, response.mapContext().route().etaSeconds());
        assertEquals(0.6, response.mapContext().route().progressRatio(), 0.0001);
        assertFalse(response.mapContext().route().progressRatioIsProbability());
        assertTrue(response.routeRecalculated());
    }

    @Test
    void hidesGeometryAndEtaWhenProviderEndpointsDoNotMatch() {
        RouteProvider provider = ignored -> new RouteProvider.AvailableRoute(
                validRoute(List.of(
                        List.of(129.0756, 35.1796),
                        List.of(129.1000, 35.2000))));

        MovementUpdateResponse response = service(provider).update(INCIDENT_ID,
                request(18, NOW.minusSeconds(30),
                        MovementUpdateRequest.JourneyState.EN_ROUTE),
                "REQ-MOVE-18", principal);

        assertEquals(MovementUpdateResponse.RouteStatus.ROUTE_ENDPOINT_MISMATCH,
                response.mapContext().route().status());
        assertNull(response.mapContext().route().geometry());
        assertNull(response.mapContext().route().etaSeconds());
        assertFalse(response.routeRecalculated());
    }

    @Test
    void doesNotCallTheProviderForAStalePosition() {
        AtomicBoolean called = new AtomicBoolean(false);
        RouteProvider provider = ignored -> {
            called.set(true);
            return new RouteProvider.UnavailableRoute("unused", false);
        };

        MovementUpdateResponse response = service(provider).update(INCIDENT_ID,
                request(19, NOW.minusSeconds(301),
                        MovementUpdateRequest.JourneyState.EN_ROUTE),
                "REQ-MOVE-19", principal);

        assertEquals(MovementUpdateResponse.RouteStatus.POSITION_STALE,
                response.mapContext().route().status());
        assertNull(response.mapContext().route().geometry());
        assertNull(response.mapContext().route().etaSeconds());
        assertFalse(called.get());
    }

    @Test
    void rejectsASequenceThatCannotAdvanceTheIncidentState() {
        MovementService service = service(
                ignored -> new RouteProvider.UnavailableRoute("not configured", false));
        service.update(INCIDENT_ID,
                request(20, NOW.minusSeconds(30),
                        MovementUpdateRequest.JourneyState.EN_ROUTE),
                "REQ-MOVE-20", principal);

        BffContractException error = assertThrows(BffContractException.class,
                () -> service.update(INCIDENT_ID,
                        request(19, NOW.minusSeconds(20),
                                MovementUpdateRequest.JourneyState.EN_ROUTE),
                        "REQ-MOVE-DUPLICATE", principal));

        assertEquals(409, error.getStatus());
        assertEquals("MOVEMENT_SEQUENCE_CONFLICT", error.getCode());
        assertEquals(20, stateStore.find(INCIDENT_ID).orElseThrow().clientSequence());
    }

    @Test
    void requiresAnAuthoritativeIncidentLocationBeforeRouting() {
        IncidentMovementContextStore emptyContextStore =
                new IncidentMovementContextStore();
        AtomicBoolean called = new AtomicBoolean(false);
        RouteProvider provider = ignored -> {
            called.set(true);
            return new RouteProvider.UnavailableRoute("unused", false);
        };
        MovementService service = new MovementService(emptyContextStore, stateStore,
                provider, new IncidentAccessPolicy(),
                Clock.fixed(NOW, ZoneOffset.UTC), new MovementProperties());

        MovementUpdateResponse response = service.update(INCIDENT_ID,
                request(23, NOW.minusSeconds(10),
                        MovementUpdateRequest.JourneyState.EN_ROUTE),
                "REQ-MOVE-23", principal);

        assertEquals(MovementUpdateResponse.RouteStatus.INCIDENT_LOCATION_REQUIRED,
                response.mapContext().route().status());
        assertNull(response.mapContext().incidentPosition());
        assertFalse(called.get());
    }

    @Test
    void returnsArrivedWithoutCallingTheProvider() {
        AtomicBoolean called = new AtomicBoolean(false);
        MovementService service = service(ignored -> {
            called.set(true);
            return new RouteProvider.UnavailableRoute("unused", false);
        });

        MovementUpdateResponse response = service.update(INCIDENT_ID,
                request(21, NOW.minusSeconds(10),
                        MovementUpdateRequest.JourneyState.ARRIVED),
                "REQ-MOVE-21", principal);

        assertEquals(MovementUpdateResponse.RouteStatus.ARRIVED,
                response.mapContext().route().status());
        assertFalse(called.get());
    }

    @Test
    void blocksDemoRoutesUnlessTheEnvironmentExplicitlyAllowsThem() {
        RouteProvider provider = ignored -> new RouteProvider.AvailableRoute(
                new RouteProvider.ServerRoute("DEMO_ROUTE_FIXTURE",
                        MovementUpdateResponse.ProviderMode.DEMO_SIMULATION,
                        "ROUTE-DEMO-1", List.of(
                        List.of(126.8311, 37.2065),
                        List.of(126.9417, 37.2181)),
                        10_000, 1_200, 4_000, 480,
                        NOW.atOffset(ZoneOffset.UTC), false,
                        "실제 길찾기 결과가 아닌 테스트 fixture"));

        MovementUpdateResponse response = service(provider).update(INCIDENT_ID,
                request(22, NOW.minusSeconds(10),
                        MovementUpdateRequest.JourneyState.EN_ROUTE),
                "REQ-MOVE-22", principal);

        assertEquals(MovementUpdateResponse.RouteStatus.ROUTE_UNAVAILABLE,
                response.mapContext().route().status());
        assertNull(response.mapContext().route().geometry());
        assertNull(response.mapContext().route().etaSeconds());
    }

    private MovementService service(RouteProvider provider) {
        return new MovementService(contextStore, stateStore, provider,
                new IncidentAccessPolicy(), Clock.fixed(NOW, ZoneOffset.UTC),
                new MovementProperties());
    }

    private MovementUpdateRequest request(long sequence, Instant observedAt,
                                          MovementUpdateRequest.JourneyState journeyState) {
        return new MovementUpdateRequest(new MovementUpdateRequest.ResponderPosition(
                37.2065, 126.8311, observedAt.atOffset(ZoneOffset.UTC),
                MovementUpdateRequest.PositionSource.MDT_DEVICE_GPS, 12.0),
                journeyState, sequence);
    }

    private RouteProvider.ServerRoute validRoute(List<List<Double>> coordinates) {
        return new RouteProvider.ServerRoute("TEST_ROUTE_PROVIDER",
                MovementUpdateResponse.ProviderMode.LIVE_API, "ROUTE-1", coordinates,
                10_000, 1_200, 4_000, 480,
                NOW.atOffset(ZoneOffset.UTC), false, "테스트 경로 제공자");
    }
}
