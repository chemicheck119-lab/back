package com.c2guard.bff.confirmation;

import com.c2guard.bff.common.BffContractException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
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
    private final ConcurrentHashMap<String, ConfirmationCancellation> cancellationsById =
            new ConcurrentHashMap<>();
    private final Set<String> reservedIds = ConcurrentHashMap.newKeySet();
    private final ConfirmationIdGenerator idGenerator;
    private final Clock clock;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public ConfirmationStore(ConfirmationIdGenerator idGenerator, Clock clock) {
        this(idGenerator, clock, null, null);
    }

    @Autowired
    public ConfirmationStore(ConfirmationIdGenerator idGenerator, Clock clock,
                             JdbcTemplate jdbcTemplate,
                             TransactionTemplate transactionTemplate) {
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
    }

    ConfirmationSaveResult save(ConfirmationSaveCommand command) {
        if (jdbcTemplate != null) {
            return saveDatabase(command);
        }
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

            if (active != null && active.status() == ConfirmationStatus.ACTIVE) {
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

    ConfirmationCancelResult cancel(ConfirmationCancelCommand command) {
        if (jdbcTemplate != null) {
            return cancelDatabase(command);
        }
        IncidentRoleKey key = new IncidentRoleKey(command.incidentId(), command.role());
        AtomicReference<ConfirmationCancelResult> result = new AtomicReference<>();
        histories.compute(key, (ignored, existing) -> {
            ConfirmationCancellation previous = cancellationsById.get(
                    command.confirmationId());
            if (previous != null) {
                requireSameCancellationTarget(previous, command);
                result.set(new ConfirmationCancelResult(previous, false));
                return existing;
            }
            if (existing == null || existing.isEmpty()) {
                throw cancellationConflict();
            }
            List<SubstanceConfirmation> history = new ArrayList<>(existing);
            SubstanceConfirmation active = history.get(history.size() - 1);
            if (active.status() != ConfirmationStatus.ACTIVE
                    || !active.confirmationId().equals(command.confirmationId())) {
                throw cancellationConflict();
            }

            Instant cancelledAt = cancellationTime();
            SubstanceConfirmation cancelled = active.cancelledAt(cancelledAt);
            ConfirmationCancellation cancellation = new ConfirmationCancellation(
                    active.confirmationId(), active.incidentId(), active.role(),
                    command.userId(), command.organizationId(), cancelledAt,
                    command.requestId());
            history.set(history.size() - 1, cancelled);
            byId.put(cancelled.confirmationId(), cancelled);
            cancellationsById.put(cancelled.confirmationId(), cancellation);
            result.set(new ConfirmationCancelResult(cancellation, true));
            return List.copyOf(history);
        });
        return result.get();
    }

    public Optional<SubstanceConfirmation> findActive(String incidentId,
                                                      ConfirmationRole role) {
        if (jdbcTemplate != null) {
            return jdbcTemplate.query("""
                            SELECT c.*
                            FROM incident_confirmation_heads h
                            JOIN substance_confirmations c
                              ON c.confirmation_id = h.active_confirmation_id
                            WHERE h.incident_id = ? AND h.confirmation_role = ?
                            """, (resultSet, rowNumber) -> mapConfirmation(resultSet),
                    incidentId, role.name()).stream().findFirst();
        }
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
        if (jdbcTemplate != null) {
            return jdbcTemplate.query("""
                            SELECT * FROM substance_confirmations
                            WHERE confirmation_id = ?
                            """, (resultSet, rowNumber) -> mapConfirmation(resultSet),
                    confirmationId).stream().findFirst();
        }
        return Optional.ofNullable(byId.get(confirmationId));
    }

    public Optional<ConfirmationCancellation> findCancellation(String confirmationId) {
        if (jdbcTemplate != null) {
            return jdbcTemplate.query("""
                            SELECT * FROM confirmation_cancellations
                            WHERE confirmation_id = ?
                            """, (resultSet, rowNumber) -> mapCancellation(resultSet),
                    confirmationId).stream().findFirst();
        }
        return Optional.ofNullable(cancellationsById.get(confirmationId));
    }

    public List<SubstanceConfirmation> history(String incidentId, ConfirmationRole role) {
        if (jdbcTemplate != null) {
            return jdbcTemplate.query("""
                            SELECT * FROM substance_confirmations
                            WHERE incident_id = ? AND confirmation_role = ?
                            ORDER BY revision
                            """, (resultSet, rowNumber) -> mapConfirmation(resultSet),
                    incidentId, role.name());
        }
        return histories.getOrDefault(new IncidentRoleKey(incidentId, role), List.of());
    }

    private ConfirmationSaveResult saveDatabase(ConfirmationSaveCommand command) {
        ensureHead(command);
        for (int attempt = 0; attempt < MAX_ID_ATTEMPTS; attempt++) {
            try {
                ConfirmationSaveResult result = transactionTemplate.execute(status -> {
                    ConfirmationHead head = jdbcTemplate.query("""
                                    SELECT active_confirmation_id, revision
                                    FROM incident_confirmation_heads
                                    WHERE incident_id = ? AND confirmation_role = ?
                                    FOR UPDATE
                                    """, (resultSet, rowNumber) -> new ConfirmationHead(
                                    resultSet.getString("active_confirmation_id"),
                                    resultSet.getLong("revision")),
                            command.incidentId(), command.role().name()).stream()
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException(
                                    "confirmation head를 생성하지 못했습니다."));

                    SubstanceConfirmation active = head.activeConfirmationId() == null
                            ? null : findById(head.activeConfirmationId()).orElseThrow(
                            () -> new IllegalStateException(
                                    "confirmation head가 존재하지 않는 record를 참조합니다."));
                    if (active != null && active.status() == ConfirmationStatus.ACTIVE
                            && active.semanticallyEquals(command)) {
                        return new ConfirmationSaveResult(active, false);
                    }

                    String confirmationId = nextDatabaseId();
                    Instant createdAt = clock.instant();
                    long revision = head.revision() + 1;
                    SubstanceConfirmation created = new SubstanceConfirmation(
                            confirmationId, command.incidentId(), command.role(),
                            command.casNumber(), command.displayName(),
                            command.confirmationBasis(), command.observedAt(),
                            command.userId(), command.organizationId(), createdAt,
                            command.requestId(), revision, ConfirmationStatus.ACTIVE,
                            null, null);

                    insert(created);
                    if (active != null) {
                        jdbcTemplate.update("""
                                        UPDATE substance_confirmations
                                        SET confirmation_status = 'SUPERSEDED',
                                            superseded_by_confirmation_id = ?,
                                            superseded_at = ?
                                        WHERE confirmation_id = ?
                                          AND confirmation_status = 'ACTIVE'
                                        """, confirmationId,
                                OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC),
                                active.confirmationId());
                    }
                    int changed = jdbcTemplate.update("""
                                    UPDATE incident_confirmation_heads
                                    SET active_confirmation_id = ?, revision = ?
                                    WHERE incident_id = ? AND confirmation_role = ?
                                      AND revision = ?
                                    """, confirmationId, revision,
                            command.incidentId(), command.role().name(), head.revision());
                    if (changed != 1) {
                        throw new IllegalStateException(
                                "confirmation revision을 원자적으로 갱신하지 못했습니다.");
                    }
                    return new ConfirmationSaveResult(created, true);
                });
                if (result != null) {
                    return result;
                }
            } catch (DuplicateKeyException collision) {
                // A generated confirmation ID collided. Retry in a fresh transaction.
            }
        }
        throw new IllegalStateException("고유한 confirmation ID를 생성하지 못했습니다.");
    }

    private ConfirmationCancelResult cancelDatabase(ConfirmationCancelCommand command) {
        ConfirmationCancelResult result = transactionTemplate.execute(status -> {
            ConfirmationHead head = jdbcTemplate.query("""
                            SELECT active_confirmation_id, revision
                            FROM incident_confirmation_heads
                            WHERE incident_id = ? AND confirmation_role = ?
                            FOR UPDATE
                            """, (resultSet, rowNumber) -> new ConfirmationHead(
                            resultSet.getString("active_confirmation_id"),
                            resultSet.getLong("revision")),
                    command.incidentId(), command.role().name()).stream()
                    .findFirst()
                    .orElseThrow(ConfirmationStore::cancellationConflict);

            Optional<ConfirmationCancellation> previous = findCancellation(
                    command.confirmationId());
            if (previous.isPresent()) {
                requireSameCancellationTarget(previous.get(), command);
                return new ConfirmationCancelResult(previous.get(), false);
            }
            if (!command.confirmationId().equals(head.activeConfirmationId())) {
                throw cancellationConflict();
            }
            SubstanceConfirmation active = findById(command.confirmationId())
                    .orElseThrow(ConfirmationStore::cancellationConflict);
            if (!active.incidentId().equals(command.incidentId())
                    || active.role() != command.role()
                    || active.status() != ConfirmationStatus.ACTIVE) {
                throw cancellationConflict();
            }

            Instant cancelledAt = cancellationTime();
            int confirmationChanged = jdbcTemplate.update("""
                            UPDATE substance_confirmations
                            SET confirmation_status = 'CANCELLED',
                                superseded_by_confirmation_id = NULL,
                                superseded_at = ?
                            WHERE confirmation_id = ?
                              AND confirmation_status = 'ACTIVE'
                            """, OffsetDateTime.ofInstant(cancelledAt, ZoneOffset.UTC),
                    command.confirmationId());
            int headChanged = jdbcTemplate.update("""
                            UPDATE incident_confirmation_heads
                            SET active_confirmation_id = NULL, revision = ?
                            WHERE incident_id = ? AND confirmation_role = ?
                              AND revision = ?
                              AND active_confirmation_id = ?
                            """, head.revision() + 1, command.incidentId(),
                    command.role().name(), head.revision(), command.confirmationId());
            if (confirmationChanged != 1 || headChanged != 1) {
                throw new IllegalStateException(
                        "confirmation 취소를 원자적으로 갱신하지 못했습니다.");
            }

            ConfirmationCancellation cancellation = new ConfirmationCancellation(
                    command.confirmationId(), command.incidentId(), command.role(),
                    command.userId(), command.organizationId(), cancelledAt,
                    command.requestId());
            jdbcTemplate.update("""
                            INSERT INTO confirmation_cancellations (
                                confirmation_id, incident_id, confirmation_role,
                                cancelled_by_user_id, cancelled_by_organization_id,
                                cancelled_at, cancelled_request_id
                            ) VALUES (?, ?, ?, ?, ?, ?, ?)
                            """, cancellation.confirmationId(), cancellation.incidentId(),
                    cancellation.role().name(), cancellation.cancelledByUserId(),
                    cancellation.cancelledByOrganizationId(),
                    OffsetDateTime.ofInstant(cancellation.cancelledAt(), ZoneOffset.UTC),
                    cancellation.cancelledRequestId());
            return new ConfirmationCancelResult(cancellation, true);
        });
        if (result == null) {
            throw new IllegalStateException("confirmation 취소 결과를 생성하지 못했습니다.");
        }
        return result;
    }

    private void ensureHead(ConfirmationSaveCommand command) {
        Integer count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM incident_confirmation_heads
                        WHERE incident_id = ? AND confirmation_role = ?
                        """, Integer.class, command.incidentId(), command.role().name());
        if (count != null && count > 0) {
            return;
        }
        try {
            transactionTemplate.executeWithoutResult(status -> jdbcTemplate.update("""
                            INSERT INTO incident_confirmation_heads (
                                incident_id, confirmation_role,
                                active_confirmation_id, revision
                            ) VALUES (?, ?, NULL, 0)
                            """, command.incidentId(), command.role().name()));
        } catch (DuplicateKeyException concurrentInsert) {
            Integer concurrentCount = jdbcTemplate.queryForObject("""
                            SELECT COUNT(*) FROM incident_confirmation_heads
                            WHERE incident_id = ? AND confirmation_role = ?
                            """, Integer.class, command.incidentId(),
                    command.role().name());
            if (concurrentCount == null || concurrentCount == 0) {
                throw concurrentInsert;
            }
        }
    }

    private void insert(SubstanceConfirmation confirmation) {
        jdbcTemplate.update("""
                        INSERT INTO substance_confirmations (
                            confirmation_id, incident_id, confirmation_role,
                            cas_number, display_name, confirmation_basis,
                            observed_at, confirmed_by_user_id,
                            confirmed_by_organization_id, created_at,
                            created_request_id, revision, confirmation_status,
                            superseded_by_confirmation_id, superseded_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, NULL)
                        """, confirmation.confirmationId(), confirmation.incidentId(),
                confirmation.role().name(), confirmation.casNumber(),
                confirmation.displayName(), confirmation.confirmationBasis().name(),
                confirmation.observedAt(), confirmation.confirmedByUserId(),
                confirmation.confirmedByOrganizationId(),
                OffsetDateTime.ofInstant(confirmation.createdAt(), ZoneOffset.UTC),
                confirmation.createdRequestId(), confirmation.revision(),
                confirmation.status().name());
    }

    private String nextDatabaseId() {
        String candidate = idGenerator.nextId();
        if (candidate == null || !candidate.matches("^[A-Za-z0-9_.:-]{1,128}$")) {
            throw new DataIntegrityViolationException("invalid generated confirmation ID");
        }
        return candidate;
    }

    private SubstanceConfirmation mapConfirmation(java.sql.ResultSet resultSet)
            throws java.sql.SQLException {
        OffsetDateTime createdAt = resultSet.getObject("created_at", OffsetDateTime.class);
        OffsetDateTime supersededAt = resultSet.getObject(
                "superseded_at", OffsetDateTime.class);
        return new SubstanceConfirmation(
                resultSet.getString("confirmation_id"),
                resultSet.getString("incident_id"),
                ConfirmationRole.valueOf(resultSet.getString("confirmation_role")),
                resultSet.getString("cas_number"),
                resultSet.getString("display_name"),
                ConfirmationBasis.valueOf(resultSet.getString("confirmation_basis")),
                resultSet.getObject("observed_at", OffsetDateTime.class),
                resultSet.getString("confirmed_by_user_id"),
                resultSet.getString("confirmed_by_organization_id"),
                createdAt.toInstant(),
                resultSet.getString("created_request_id"),
                resultSet.getLong("revision"),
                ConfirmationStatus.valueOf(resultSet.getString("confirmation_status")),
                resultSet.getString("superseded_by_confirmation_id"),
                supersededAt == null ? null : supersededAt.toInstant());
    }

    private ConfirmationCancellation mapCancellation(java.sql.ResultSet resultSet)
            throws java.sql.SQLException {
        OffsetDateTime cancelledAt = resultSet.getObject(
                "cancelled_at", OffsetDateTime.class);
        return new ConfirmationCancellation(
                resultSet.getString("confirmation_id"),
                resultSet.getString("incident_id"),
                ConfirmationRole.valueOf(resultSet.getString("confirmation_role")),
                resultSet.getString("cancelled_by_user_id"),
                resultSet.getString("cancelled_by_organization_id"),
                cancelledAt.toInstant(),
                resultSet.getString("cancelled_request_id"));
    }

    private static void requireSameCancellationTarget(
            ConfirmationCancellation cancellation,
            ConfirmationCancelCommand command) {
        if (!cancellation.incidentId().equals(command.incidentId())
                || cancellation.role() != command.role()) {
            throw cancellationConflict();
        }
    }

    private static BffContractException cancellationConflict() {
        return new BffContractException(409, "INCIDENT_REFERENCE_CONFLICT",
                "현재 활성 confirmation과 취소 대상이 일치하지 않습니다. 최신 상태를 다시 확인하세요.",
                true);
    }

    private Instant cancellationTime() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
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

    private record ConfirmationHead(String activeConfirmationId, long revision) {
    }
}
