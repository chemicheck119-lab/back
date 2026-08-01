package com.c2guard.bff.movement;

import java.time.OffsetDateTime;
import java.util.List;

public interface RouteProvider {

    RouteResult findRoute(RouteRequest request);

    record RouteRequest(
            RoutePoint origin,
            RoutePoint destination,
            OffsetDateTime requestedAt
    ) {
    }

    record RoutePoint(double latitude, double longitude) {
    }

    sealed interface RouteResult permits AvailableRoute, UnavailableRoute {
    }

    record AvailableRoute(ServerRoute route) implements RouteResult {
    }

    record UnavailableRoute(String reason, boolean retryable) implements RouteResult {
    }

    record ServerRoute(
            String provider,
            MovementUpdateResponse.ProviderMode mode,
            String routeId,
            List<List<Double>> coordinates,
            int distanceM,
            int durationSeconds,
            int remainingDistanceM,
            int remainingDurationSeconds,
            OffsetDateTime generatedAt,
            boolean trafficApplied,
            String attribution
    ) {
        public ServerRoute {
            coordinates = coordinates == null
                    ? List.of()
                    : coordinates.stream().map(List::copyOf).toList();
        }
    }
}
