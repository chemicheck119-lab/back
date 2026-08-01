package com.c2guard.bff.movement;

import com.c2guard.bff.incident.IncidentAnalyzeRequest;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class IncidentMovementContextStore {

    private final ConcurrentMap<String, IncidentContext> contexts = new ConcurrentHashMap<>();

    public void capture(String incidentId, IncidentAnalyzeRequest request) {
        IncidentAnalyzeRequest.IncidentLocation location = request.location();
        String stationName = request.operationsContext() == null
                ? null : normalized(request.operationsContext().dispatchStationName());
        if (location == null || location.latitude() == null || location.longitude() == null) {
            if (stationName != null) {
                contexts.computeIfPresent(incidentId,
                        (ignored, existing) -> existing.withResponderLabel(stationName));
            }
            return;
        }
        String label = bounded(firstPresent(location.facilityName(), location.address(),
                "사고 위치"), 200);
        String source = location.coordinateSource() == null
                ? "UNSPECIFIED_COORDINATE_SOURCE" : location.coordinateSource().name();
        boolean simulation = location.coordinateSource()
                == IncidentAnalyzeRequest.CoordinateSource.DEMO_FIXTURE;
        save(incidentId, new IncidentPosition(location.latitude(), location.longitude(),
                label, source, location.resolvedAt(), simulation), stationName);
    }

    public void save(String incidentId, IncidentPosition incidentPosition,
                     String responderLabel) {
        contexts.put(incidentId, new IncidentContext(incidentPosition,
                firstPresent(responderLabel, "출동 대원 위치")));
    }

    public Optional<IncidentContext> find(String incidentId) {
        return Optional.ofNullable(contexts.get(incidentId));
    }

    private static String firstPresent(String... values) {
        for (String value : values) {
            String normalized = normalized(value);
            if (normalized != null) {
                return normalized;
            }
        }
        throw new IllegalArgumentException("at least one value is required");
    }

    private static String normalized(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String bounded(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }

    public record IncidentContext(IncidentPosition incidentPosition,
                                  String responderLabel) {
        IncidentContext withResponderLabel(String label) {
            return new IncidentContext(incidentPosition, label);
        }
    }

    public record IncidentPosition(
            double latitude,
            double longitude,
            String label,
            String source,
            OffsetDateTime observedAt,
            boolean simulation
    ) {
    }
}
