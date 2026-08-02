package com.c2guard.bff.intake;

import com.c2guard.bff.common.BffContractException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SyntheticIncidentReplayRegistry {

    private final ConcurrentHashMap<String, RegisteredIncident> incidents =
            new ConcurrentHashMap<>();
    private final IncidentReplayProperties properties;
    private final Clock clock;

    public SyntheticIncidentReplayRegistry(IncidentReplayProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        if (properties.getSyntheticIncidentTtl() == null
                || properties.getSyntheticIncidentTtl().isZero()
                || properties.getSyntheticIncidentTtl().isNegative()
                || properties.getMaxActiveSyntheticIncidents() < 1) {
            throw new IllegalArgumentException(
                    "합성 incident TTL과 최대 보관 수는 양수여야 합니다.");
        }
    }

    public void register(IncidentEnvelope envelope) {
        requireSafeBoundary(envelope);
        Instant now = clock.instant();
        removeExpired(now);
        if (incidents.size() >= properties.getMaxActiveSyntheticIncidents()) {
            oldest().ifPresent(incidents::remove);
        }
        incidents.put(envelope.incidentId(), new RegisteredIncident(
                envelope, now.plus(properties.getSyntheticIncidentTtl())));
    }

    public IncidentEnvelope requireActive(String incidentId) {
        RegisteredIncident registered = incidents.get(incidentId);
        if (registered == null) {
            throw new BffContractException(404, "SYNTHETIC_INCIDENT_NOT_REGISTERED",
                    "현재 BE가 발급한 공개 합성 지령이 아닙니다.", false);
        }
        if (!clock.instant().isBefore(registered.expiresAt())) {
            incidents.remove(incidentId, registered);
            throw new BffContractException(410, "SYNTHETIC_INCIDENT_EXPIRED",
                    "공개 합성 지령의 확인 가능 시간이 만료되었습니다. 지령을 다시 수신하세요.",
                    false);
        }
        requireSafeBoundary(registered.envelope());
        return registered.envelope();
    }

    private void removeExpired(Instant now) {
        incidents.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt()));
    }

    private Optional<String> oldest() {
        return incidents.entrySet().stream()
                .min(Comparator.comparing(entry -> entry.getValue().expiresAt()))
                .map(java.util.Map.Entry::getKey);
    }

    private static void requireSafeBoundary(IncidentEnvelope envelope) {
        if (envelope == null
                || envelope.sourceType() != IncidentSourceType.SYNTHETIC_DISPATCH_REPLAY
                || envelope.dataClassification() != IncidentDataClassification.PUBLIC_SYNTHETIC
                || envelope.containsPersonalInformation()
                || !"CHEMICHECK119_PUBLIC_REPLAY".equals(envelope.sourceProvider())) {
            throw new IllegalArgumentException("공개 합성 지령 경계를 확인할 수 없습니다.");
        }
    }

    private record RegisteredIncident(IncidentEnvelope envelope, Instant expiresAt) {
    }
}
