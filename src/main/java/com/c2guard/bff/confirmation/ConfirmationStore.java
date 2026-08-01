package com.c2guard.bff.confirmation;

import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Repository
public class ConfirmationStore {

    private static final int MAX_ID_ATTEMPTS = 5;

    private final ConcurrentHashMap<IncidentRoleKey, List<SubstanceConfirmation>> histories =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SubstanceConfirmation> byId =
            new ConcurrentHashMap<>();
    private final Set<String> reservedIds = ConcurrentHashMap.newKeySet();
    private final ConfirmationIdGenerator idGenerator;
    private final Clock clock;

    public ConfirmationStore(ConfirmationIdGenerator idGenerator, Clock clock) {
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    ConfirmationSaveResult save(ConfirmationSaveCommand command) {
        IncidentRoleKey key = new IncidentRoleKey(command.incidentId(), command.role());
        AtomicReference<ConfirmationSaveResult> result = new AtomicReference<>();
        histories.compute(key, (ignored, existing) -> {
            List<SubstanceConfirmation> history = existing == null
                    ? new ArrayList<>() : new ArrayList<>(existing);
            SubstanceConfirmation active = history.isEmpty()
                    ? null : history.get(history.size() - 1);
            if (active != null && active.status() == ConfirmationStatus.ACTIVE
                    && active.semanticallyEquals(command)) {
                result.set(new ConfirmationSaveResult(active, false));
                return existing;
            }

            Instant createdAt = clock.instant();
            String confirmationId = reserveId();
            long revision = active == null ? 1 : active.revision() + 1;
            SubstanceConfirmation created = new SubstanceConfirmation(
                    confirmationId, command.incidentId(), command.role(), command.casNumber(),
                    command.displayName(), command.confirmationBasis(), command.observedAt(),
                    command.userId(), command.organizationId(), createdAt, command.requestId(),
                    revision, ConfirmationStatus.ACTIVE, null, null);

            if (active != null) {
                SubstanceConfirmation superseded = active.supersededBy(confirmationId, createdAt);
                history.set(history.size() - 1, superseded);
                byId.put(superseded.confirmationId(), superseded);
            }
            history.add(created);
            byId.put(created.confirmationId(), created);
            result.set(new ConfirmationSaveResult(created, true));
            return List.copyOf(history);
        });
        return result.get();
    }

    public Optional<SubstanceConfirmation> findActive(String incidentId,
                                                      ConfirmationRole role) {
        List<SubstanceConfirmation> history = histories.get(
                new IncidentRoleKey(incidentId, role));
        if (history == null || history.isEmpty()) {
            return Optional.empty();
        }
        SubstanceConfirmation latest = history.get(history.size() - 1);
        return latest.status() == ConfirmationStatus.ACTIVE
                ? Optional.of(latest) : Optional.empty();
    }

    public Map<ConfirmationRole, SubstanceConfirmation> findActiveForIncident(
            String incidentId) {
        Map<ConfirmationRole, SubstanceConfirmation> result =
                new EnumMap<>(ConfirmationRole.class);
        for (ConfirmationRole role : ConfirmationRole.values()) {
            findActive(incidentId, role).ifPresent(value -> result.put(role, value));
        }
        return Map.copyOf(result);
    }

    public Optional<SubstanceConfirmation> findById(String confirmationId) {
        return Optional.ofNullable(byId.get(confirmationId));
    }

    public List<SubstanceConfirmation> history(String incidentId, ConfirmationRole role) {
        return histories.getOrDefault(new IncidentRoleKey(incidentId, role), List.of());
    }

    private String reserveId() {
        for (int attempt = 0; attempt < MAX_ID_ATTEMPTS; attempt++) {
            String candidate = idGenerator.nextId();
            if (candidate != null && candidate.matches("^[A-Za-z0-9_.:-]{1,128}$")
                    && reservedIds.add(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("고유한 confirmation ID를 생성하지 못했습니다.");
    }

    private record IncidentRoleKey(String incidentId, ConfirmationRole role) {
    }
}
