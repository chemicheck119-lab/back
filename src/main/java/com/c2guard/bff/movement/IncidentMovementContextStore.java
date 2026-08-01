package com.c2guard.bff.movement;

import com.c2guard.bff.incident.IncidentAnalyzeRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class IncidentMovementContextStore {

    private final ConcurrentMap<String, IncidentContext> contexts = new ConcurrentHashMap<>();
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public IncidentMovementContextStore() {
        this(null, null, Clock.systemUTC());
    }

    @Autowired
    public IncidentMovementContextStore(JdbcTemplate jdbcTemplate,
                                        TransactionTemplate transactionTemplate,
                                        Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    public void capture(String incidentId, IncidentAnalyzeRequest request) {
        IncidentAnalyzeRequest.IncidentLocation location = request.location();
        String stationName = request.operationsContext() == null
                ? null : normalized(request.operationsContext().dispatchStationName());
        if (location == null || location.latitude() == null || location.longitude() == null) {
            if (stationName != null) {
                if (jdbcTemplate != null) {
                    jdbcTemplate.update("""
                                    UPDATE incident_movement_contexts
                                    SET responder_label = ?, updated_at = ?
                                    WHERE incident_id = ?
                                    """, stationName,
                            OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC),
                            incidentId);
                    return;
                }
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
        if (jdbcTemplate != null) {
            saveDatabase(incidentId, incidentPosition,
                    firstPresent(responderLabel, "출동 대원 위치"));
            return;
        }
        contexts.put(incidentId, new IncidentContext(incidentPosition,
                firstPresent(responderLabel, "출동 대원 위치")));
    }

    public Optional<IncidentContext> find(String incidentId) {
        if (jdbcTemplate != null) {
            List<IncidentContext> found = jdbcTemplate.query("""
                            SELECT latitude, longitude, location_label,
                                   coordinate_source, observed_at, simulation,
                                   responder_label
                            FROM incident_movement_contexts
                            WHERE incident_id = ?
                            """, (resultSet, rowNumber) -> {
                        OffsetDateTime observedAt = resultSet.getObject(
                                "observed_at", OffsetDateTime.class);
                        IncidentPosition position = new IncidentPosition(
                                resultSet.getDouble("latitude"),
                                resultSet.getDouble("longitude"),
                                resultSet.getString("location_label"),
                                resultSet.getString("coordinate_source"),
                                observedAt,
                                resultSet.getBoolean("simulation"));
                        return new IncidentContext(position,
                                resultSet.getString("responder_label"));
                    }, incidentId);
            return found.stream().findFirst();
        }
        return Optional.ofNullable(contexts.get(incidentId));
    }

    private void saveDatabase(String incidentId, IncidentPosition position,
                              String responderLabel) {
        OffsetDateTime updatedAt = OffsetDateTime.ofInstant(
                clock.instant(), ZoneOffset.UTC);
        int updated = updateExisting(incidentId, position, responderLabel, updatedAt);
        if (updated == 1) {
            return;
        }
        try {
            transactionTemplate.executeWithoutResult(status -> jdbcTemplate.update("""
                            INSERT INTO incident_movement_contexts (
                                incident_id, latitude, longitude, location_label,
                                coordinate_source, observed_at, simulation,
                                responder_label, updated_at
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """, incidentId, position.latitude(), position.longitude(),
                    position.label(), position.source(), position.observedAt(),
                    position.simulation(), responderLabel, updatedAt));
        } catch (DuplicateKeyException concurrentInsert) {
            if (updateExisting(incidentId, position, responderLabel, updatedAt) != 1) {
                throw concurrentInsert;
            }
        }
    }

    private int updateExisting(String incidentId, IncidentPosition position,
                               String responderLabel, OffsetDateTime updatedAt) {
        Integer updated = transactionTemplate.execute(status -> jdbcTemplate.update("""
                        UPDATE incident_movement_contexts
                        SET latitude = ?, longitude = ?, location_label = ?,
                            coordinate_source = ?, observed_at = ?,
                            simulation = ?, responder_label = ?, updated_at = ?
                        WHERE incident_id = ?
                        """, position.latitude(), position.longitude(), position.label(),
                position.source(), position.observedAt(), position.simulation(),
                responderLabel, updatedAt, incidentId));
        return updated == null ? 0 : updated;
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
