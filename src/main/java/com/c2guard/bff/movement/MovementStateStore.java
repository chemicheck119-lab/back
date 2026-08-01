package com.c2guard.bff.movement;

import com.c2guard.bff.common.BffContractException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class MovementStateStore {

    private final ConcurrentMap<String, MovementState> states = new ConcurrentHashMap<>();
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public MovementStateStore() {
        this(null, null);
    }

    @Autowired
    public MovementStateStore(JdbcTemplate jdbcTemplate,
                              TransactionTemplate transactionTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
    }

    public MovementState accept(String incidentId, MovementUpdateRequest request,
                                OffsetDateTime acceptedAt) {
        if (jdbcTemplate != null) {
            return acceptDatabase(incidentId, request, acceptedAt);
        }
        return states.compute(incidentId, (ignored, current) -> {
            if (current != null && request.clientSequence() <= current.clientSequence()) {
                throw new BffContractException(409, "MOVEMENT_SEQUENCE_CONFLICT",
                        "clientSequence는 사고별 최신 값보다 커야 합니다.", false);
            }
            return new MovementState(request.clientSequence(), request.responderPosition(),
                    request.journeyState(), acceptedAt);
        });
    }

    public Optional<MovementState> find(String incidentId) {
        if (jdbcTemplate != null) {
            List<MovementState> found = jdbcTemplate.query("""
                            SELECT client_sequence, responder_latitude,
                                   responder_longitude, responder_accuracy_meters,
                                   responder_observed_at, responder_source,
                                   journey_state, accepted_at
                            FROM incident_movement_states
                            WHERE incident_id = ?
                            """, (resultSet, rowNumber) -> mapState(resultSet),
                    incidentId);
            return found.stream().findFirst();
        }
        return Optional.ofNullable(states.get(incidentId));
    }

    private MovementState acceptDatabase(String incidentId, MovementUpdateRequest request,
                                         OffsetDateTime acceptedAt) {
        MovementState accepted = new MovementState(request.clientSequence(),
                request.responderPosition(), request.journeyState(), acceptedAt);
        MovementUpdateRequest.ResponderPosition position = request.responderPosition();
        for (int attempt = 0; attempt < 3; attempt++) {
            Integer changed = transactionTemplate.execute(status -> jdbcTemplate.update("""
                            UPDATE incident_movement_states
                            SET client_sequence = ?, responder_latitude = ?,
                                responder_longitude = ?, responder_accuracy_meters = ?,
                                responder_observed_at = ?, responder_source = ?,
                                journey_state = ?, accepted_at = ?
                            WHERE incident_id = ? AND client_sequence < ?
                            """, request.clientSequence(), position.latitude(),
                    position.longitude(), position.accuracyM(), position.observedAt(),
                    position.source().name(), request.journeyState().name(), acceptedAt,
                    incidentId, request.clientSequence()));
            if (changed != null && changed == 1) {
                return accepted;
            }
            try {
                transactionTemplate.executeWithoutResult(status -> jdbcTemplate.update("""
                            INSERT INTO incident_movement_states (
                                incident_id, client_sequence, responder_latitude,
                                responder_longitude, responder_accuracy_meters,
                                responder_observed_at, responder_source,
                                journey_state, accepted_at
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """, incidentId, request.clientSequence(),
                        position.latitude(), position.longitude(), position.accuracyM(),
                        position.observedAt(), position.source().name(),
                        request.journeyState().name(), acceptedAt));
                return accepted;
            } catch (DuplicateKeyException concurrentInsert) {
                // Re-run the ordered update in case a lower sequence was inserted concurrently.
            }
        }
        throw sequenceConflict();
    }

    private MovementState mapState(java.sql.ResultSet resultSet)
            throws java.sql.SQLException {
        Double accuracy = resultSet.getObject("responder_accuracy_meters") == null
                ? null : resultSet.getDouble("responder_accuracy_meters");
        MovementUpdateRequest.ResponderPosition position =
                new MovementUpdateRequest.ResponderPosition(
                        resultSet.getDouble("responder_latitude"),
                        resultSet.getDouble("responder_longitude"),
                        resultSet.getObject("responder_observed_at", OffsetDateTime.class),
                        MovementUpdateRequest.PositionSource.valueOf(
                                resultSet.getString("responder_source")),
                        accuracy);
        return new MovementState(
                resultSet.getLong("client_sequence"), position,
                MovementUpdateRequest.JourneyState.valueOf(
                        resultSet.getString("journey_state")),
                resultSet.getObject("accepted_at", OffsetDateTime.class));
    }

    private static BffContractException sequenceConflict() {
        return new BffContractException(409, "MOVEMENT_SEQUENCE_CONFLICT",
                "clientSequence는 사고별 최신 값보다 커야 합니다.", false);
    }

    public record MovementState(
            long clientSequence,
            MovementUpdateRequest.ResponderPosition responderPosition,
            MovementUpdateRequest.JourneyState journeyState,
            OffsetDateTime acceptedAt
    ) {
    }
}
