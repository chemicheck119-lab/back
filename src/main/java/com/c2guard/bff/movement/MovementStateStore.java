package com.c2guard.bff.movement;

import com.c2guard.bff.common.BffContractException;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class MovementStateStore {

    private final ConcurrentMap<String, MovementState> states = new ConcurrentHashMap<>();

    public MovementState accept(String incidentId, MovementUpdateRequest request,
                                OffsetDateTime acceptedAt) {
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
        return Optional.ofNullable(states.get(incidentId));
    }

    public record MovementState(
            long clientSequence,
            MovementUpdateRequest.ResponderPosition responderPosition,
            MovementUpdateRequest.JourneyState journeyState,
            OffsetDateTime acceptedAt
    ) {
    }
}
