package com.c2guard.bff.movement;

import com.c2guard.security.BffUserPrincipal;
import com.c2guard.security.IncidentAccessPolicy;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static com.c2guard.bff.movement.MovementUpdateResponse.ProviderMode.DEMO_SIMULATION;
import static com.c2guard.bff.movement.MovementUpdateResponse.RouteStatus.ARRIVED;
import static com.c2guard.bff.movement.MovementUpdateResponse.RouteStatus.AVAILABLE;
import static com.c2guard.bff.movement.MovementUpdateResponse.RouteStatus.INCIDENT_LOCATION_REQUIRED;
import static com.c2guard.bff.movement.MovementUpdateResponse.RouteStatus.POSITION_STALE;
import static com.c2guard.bff.movement.MovementUpdateResponse.RouteStatus.ROUTE_ENDPOINT_MISMATCH;
import static com.c2guard.bff.movement.MovementUpdateResponse.RouteStatus.ROUTE_UNAVAILABLE;

@Service
public class MovementService {

    private static final Duration MAX_FUTURE_SKEW = Duration.ofMinutes(5);
    private static final Duration POSITION_MAX_AGE = Duration.ofMinutes(5);
    private static final double MAX_ENDPOINT_DISTANCE_M = 1_500.0;

    private final IncidentMovementContextStore contextStore;
    private final MovementStateStore stateStore;
    private final RouteProvider routeProvider;
    private final IncidentAccessPolicy incidentAccessPolicy;
    private final Clock clock;
    private final MovementProperties properties;

    public MovementService(IncidentMovementContextStore contextStore,
                           MovementStateStore stateStore,
                           RouteProvider routeProvider,
                           IncidentAccessPolicy incidentAccessPolicy,
                           Clock clock,
                           MovementProperties properties) {
        this.contextStore = contextStore;
        this.stateStore = stateStore;
        this.routeProvider = routeProvider;
        this.incidentAccessPolicy = incidentAccessPolicy;
        this.clock = clock;
        this.properties = properties;
    }

    public MovementUpdateResponse update(String incidentId, MovementUpdateRequest request,
                                         String requestId, BffUserPrincipal principal) {
        incidentAccessPolicy.requireAccess(principal, incidentId);
        OffsetDateTime acceptedAt = clock.instant().atOffset(ZoneOffset.UTC);
        if (request.responderPosition().observedAt().toInstant()
                .isAfter(clock.instant().plus(MAX_FUTURE_SKEW))) {
            throw new IllegalArgumentException(
                    "observedAt은 서버 시각보다 5분 이상 미래일 수 없습니다.");
        }
        stateStore.accept(incidentId, request, acceptedAt);

        IncidentMovementContextStore.IncidentContext context =
                contextStore.find(incidentId).orElse(null);
        MovementUpdateResponse.MapPoint incidentPosition = context == null
                ? null : incidentPoint(context.incidentPosition());
        MovementUpdateResponse.MapPoint responderPosition = responderPoint(request, context);

        RouteDecision decision = decideRoute(request, acceptedAt, context);
        return new MovementUpdateResponse(requestId, incidentId, acceptedAt,
                request.clientSequence(), new MovementUpdateResponse.MapContext(
                incidentPosition, responderPosition, decision.route()),
                properties.getNextRefreshSeconds(), decision.recalculated());
    }

    private RouteDecision decideRoute(MovementUpdateRequest request,
                                      OffsetDateTime acceptedAt,
                                      IncidentMovementContextStore.IncidentContext context) {
        if (request.journeyState() == MovementUpdateRequest.JourneyState.ARRIVED
                || request.journeyState() == MovementUpdateRequest.JourneyState.ON_SCENE) {
            return unavailable(ARRIVED,
                    "현장 도착 상태이므로 이동 경로와 ETA를 표시하지 않습니다.");
        }
        if (request.responderPosition().source()
                != MovementUpdateRequest.PositionSource.MANUAL_DISPATCH
                && Duration.between(request.responderPosition().observedAt().toInstant(),
                acceptedAt.toInstant()).compareTo(POSITION_MAX_AGE) > 0) {
            return unavailable(POSITION_STALE,
                    "현재 위치가 5분보다 오래되어 경로와 ETA를 표시하지 않습니다.");
        }
        if (context == null) {
            return unavailable(INCIDENT_LOCATION_REQUIRED,
                    "검증된 사고 위치가 없어 경로를 계산할 수 없습니다.");
        }

        RouteProvider.RouteResult result = routeProvider.findRoute(
                new RouteProvider.RouteRequest(
                        new RouteProvider.RoutePoint(request.responderPosition().latitude(),
                                request.responderPosition().longitude()),
                        new RouteProvider.RoutePoint(context.incidentPosition().latitude(),
                                context.incidentPosition().longitude()),
                        acceptedAt));
        if (result == null) {
            return unavailable(ROUTE_UNAVAILABLE,
                    "길찾기 제공자가 유효한 응답을 반환하지 않았습니다.");
        }
        if (result instanceof RouteProvider.UnavailableRoute unavailable) {
            String reason = unavailable.reason() == null || unavailable.reason().isBlank()
                    ? "길찾기 제공자를 사용할 수 없습니다." : unavailable.reason();
            return unavailable(ROUTE_UNAVAILABLE, boundedMessage(reason));
        }
        return available(((RouteProvider.AvailableRoute) result).route(), request, context);
    }

    private RouteDecision available(RouteProvider.ServerRoute route,
                                    MovementUpdateRequest request,
                                    IncidentMovementContextStore.IncidentContext context) {
        if (!isStructurallyValid(route)) {
            return unavailable(ROUTE_UNAVAILABLE,
                    "길찾기 응답이 지도 계약을 충족하지 않아 경로와 ETA를 숨겼습니다.");
        }
        if (route.mode() == DEMO_SIMULATION && !properties.isAllowDemoSimulation()) {
            return unavailable(ROUTE_UNAVAILABLE,
                    "이 실행 환경에서는 시뮬레이션 경로를 사용할 수 없습니다.");
        }
        List<Double> start = route.coordinates().get(0);
        List<Double> end = route.coordinates().get(route.coordinates().size() - 1);
        double startDistance = distanceM(request.responderPosition().latitude(),
                request.responderPosition().longitude(), start.get(1), start.get(0));
        double endDistance = distanceM(context.incidentPosition().latitude(),
                context.incidentPosition().longitude(), end.get(1), end.get(0));
        if (startDistance > MAX_ENDPOINT_DISTANCE_M || endDistance > MAX_ENDPOINT_DISTANCE_M) {
            return unavailable(ROUTE_ENDPOINT_MISMATCH,
                    "경로 끝점이 현재 위치 또는 사고 위치와 일치하지 않아 경로와 ETA를 숨겼습니다.");
        }

        double progressRatio = Math.max(0.0, Math.min(1.0,
                1.0 - ((double) route.remainingDistanceM() / route.distanceM())));
        MovementUpdateResponse.RouteStatus status = route.mode() == DEMO_SIMULATION
                ? MovementUpdateResponse.RouteStatus.DEMO_SIMULATION : AVAILABLE;
        MovementUpdateResponse.RouteState state = new MovementUpdateResponse.RouteState(
                status, route.provider(), route.mode(), route.routeId(),
                new MovementUpdateResponse.RouteGeometry(route.coordinates()),
                route.distanceM(), route.remainingDistanceM(),
                route.remainingDurationSeconds(), progressRatio, false,
                route.trafficApplied(), route.generatedAt(), route.attribution(),
                status == MovementUpdateResponse.RouteStatus.DEMO_SIMULATION
                        ? "실제 길찾기 결과가 아닌 시뮬레이션 경로입니다."
                        : "서버에서 확인한 도로 경로입니다.");
        return new RouteDecision(state, true);
    }

    private boolean isStructurallyValid(RouteProvider.ServerRoute route) {
        if (route == null || !hasLength(route.provider(), 80)
                || route.mode() == null || !hasLength(route.routeId(), 200)
                || route.distanceM() <= 0 || route.distanceM() > 5_000_000
                || route.durationSeconds() <= 0 || route.durationSeconds() > 604_800
                || route.remainingDistanceM() < 0
                || route.remainingDistanceM() > route.distanceM()
                || route.remainingDurationSeconds() < 0
                || route.remainingDurationSeconds() > route.durationSeconds()
                || route.generatedAt() == null || !hasLength(route.attribution(), 300)
                || route.coordinates().size() < 2
                || route.coordinates().size() > 10_000) {
            return false;
        }
        return route.coordinates().stream().allMatch(this::isCoordinate);
    }

    private boolean hasLength(String value, int maximum) {
        return value != null && !value.isBlank() && value.length() <= maximum;
    }

    private boolean isCoordinate(List<Double> coordinate) {
        if (coordinate == null || coordinate.size() != 2
                || coordinate.get(0) == null || coordinate.get(1) == null) {
            return false;
        }
        double longitude = coordinate.get(0);
        double latitude = coordinate.get(1);
        return Double.isFinite(longitude) && longitude >= -180 && longitude <= 180
                && Double.isFinite(latitude) && latitude >= -90 && latitude <= 90;
    }

    private MovementUpdateResponse.MapPoint incidentPoint(
            IncidentMovementContextStore.IncidentPosition source) {
        return new MovementUpdateResponse.MapPoint(source.latitude(), source.longitude(),
                source.label(), source.source(), source.observedAt(), null,
                source.simulation());
    }

    private MovementUpdateResponse.MapPoint responderPoint(MovementUpdateRequest request,
            IncidentMovementContextStore.IncidentContext context) {
        MovementUpdateRequest.ResponderPosition source = request.responderPosition();
        String label = context == null ? "출동 대원 위치" : context.responderLabel();
        return new MovementUpdateResponse.MapPoint(source.latitude(), source.longitude(),
                label, source.source().name(), source.observedAt(), source.accuracyM(),
                source.source() == MovementUpdateRequest.PositionSource.DEMO_SIMULATION);
    }

    private RouteDecision unavailable(MovementUpdateResponse.RouteStatus status,
                                      String message) {
        return new RouteDecision(MovementUpdateResponse.RouteState.unavailable(status,
                boundedMessage(message)), false);
    }

    private String boundedMessage(String message) {
        String normalized = message == null || message.isBlank()
                ? "경로 상태를 확인할 수 없습니다." : message.trim();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
    }

    private double distanceM(double latitudeA, double longitudeA,
                             double latitudeB, double longitudeB) {
        double earthRadiusM = 6_371_000.0;
        double latitudeDelta = Math.toRadians(latitudeB - latitudeA);
        double longitudeDelta = Math.toRadians(longitudeB - longitudeA);
        double value = Math.sin(latitudeDelta / 2) * Math.sin(latitudeDelta / 2)
                + Math.cos(Math.toRadians(latitudeA)) * Math.cos(Math.toRadians(latitudeB))
                * Math.sin(longitudeDelta / 2) * Math.sin(longitudeDelta / 2);
        return earthRadiusM * 2 * Math.atan2(Math.sqrt(value), Math.sqrt(1 - value));
    }

    private record RouteDecision(MovementUpdateResponse.RouteState route,
                                 boolean recalculated) {
    }
}
