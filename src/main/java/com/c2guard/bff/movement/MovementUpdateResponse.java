package com.c2guard.bff.movement;

import java.time.OffsetDateTime;
import java.util.List;

public record MovementUpdateResponse(
        String schemaVersion,
        String requestId,
        String incidentId,
        OffsetDateTime acceptedAt,
        long clientSequence,
        MapContext mapContext,
        int nextRefreshSeconds,
        boolean routeRecalculated
) {
    public static final String SCHEMA_VERSION = "chemicheck119-dashboard-bff-v1";

    public MovementUpdateResponse(String requestId, String incidentId,
                                  OffsetDateTime acceptedAt, long clientSequence,
                                  MapContext mapContext, int nextRefreshSeconds,
                                  boolean routeRecalculated) {
        this(SCHEMA_VERSION, requestId, incidentId, acceptedAt, clientSequence,
                mapContext, nextRefreshSeconds, routeRecalculated);
    }

    public record MapContext(
            String coverageScope,
            MapPoint incidentPosition,
            MapPoint responderPosition,
            RouteState route,
            RenderingContract rendering,
            String hazardOverlayStatus
    ) {
        public MapContext(MapPoint incidentPosition, MapPoint responderPosition,
                          RouteState route) {
            this("NATIONWIDE_KOREA", incidentPosition, responderPosition, route,
                    RenderingContract.standard(),
                    "NOT_COMPUTED_NO_VALIDATED_DISPERSION_MODEL");
        }
    }

    public record MapPoint(
            double latitude,
            double longitude,
            String label,
            String source,
            OffsetDateTime observedAt,
            Double accuracyM,
            boolean isSimulation
    ) {
    }

    public record RouteState(
            RouteStatus status,
            String provider,
            ProviderMode providerMode,
            String routeId,
            RouteGeometry geometry,
            Integer totalDistanceM,
            Integer remainingDistanceM,
            Integer etaSeconds,
            Double progressRatio,
            boolean progressRatioIsProbability,
            Boolean trafficApplied,
            OffsetDateTime generatedAt,
            String attribution,
            String message
    ) {
        public static RouteState unavailable(RouteStatus status, String message) {
            return new RouteState(status, null, null, null, null,
                    null, null, null, null, false, null, null, null, message);
        }
    }

    public enum RouteStatus {
        AVAILABLE,
        DEMO_SIMULATION,
        ROUTE_UNAVAILABLE,
        INCIDENT_LOCATION_REQUIRED,
        RESPONDER_POSITION_REQUIRED,
        POSITION_STALE,
        ROUTE_ENDPOINT_MISMATCH,
        ARRIVED
    }

    public enum ProviderMode {
        LIVE_API,
        CACHED_API,
        DEMO_SIMULATION
    }

    public record RouteGeometry(String type, List<List<Double>> coordinates) {
        public RouteGeometry(List<List<Double>> coordinates) {
            this("LineString", coordinates.stream().map(List::copyOf).toList());
        }
    }

    public record RenderingContract(
            String geometryFormat,
            String recommendedRenderer,
            boolean tileProviderRequired,
            boolean attributionRequired,
            boolean publicOsmStandardTilesForProduction,
            boolean routeAnimationSupported
    ) {
        static RenderingContract standard() {
            return new RenderingContract("GEOJSON_RFC7946", "MAPLIBRE_GL_JS",
                    true, true, false, true);
        }
    }
}
