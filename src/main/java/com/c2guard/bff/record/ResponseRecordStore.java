package com.c2guard.bff.record;

import com.c2guard.bff.confirmation.SubstanceConfirmation;
import com.c2guard.bff.incident.IncidentAgentMemoryStore;
import com.c2guard.bff.incident.IncidentAnalysisSnapshotStore;
import com.c2guard.bff.movement.IncidentMovementContextStore;
import com.c2guard.bff.movement.MovementStateStore;
import com.c2guard.security.BffUserPrincipal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Repository
public class ResponseRecordStore {

    private static final int MAX_ID_ATTEMPTS = 5;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final RecordIdGenerator idGenerator;
    private final Clock clock;
    private final StructuredIncidentRecordProjector structuredProjector =
            new StructuredIncidentRecordProjector();

    public ResponseRecordStore(JdbcTemplate jdbcTemplate,
                               TransactionTemplate transactionTemplate,
                               ObjectMapper objectMapper,
                               RecordIdGenerator idGenerator,
                               Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    StoredResponseRecord save(String incidentId, RecordSaveRequest request,
                              String fingerprint, String requestId,
                              BffUserPrincipal principal,
                              List<IncidentAnalysisSnapshotStore.Snapshot> analyses,
                              List<SubstanceConfirmation> confirmations,
                              Optional<IncidentAgentMemoryStore.StoredMemory> agentMemory,
                              Optional<IncidentMovementContextStore.IncidentContext> movementContext,
                              Optional<MovementStateStore.MovementState> movementState) {
        ensureIncident(incidentId);
        Optional<StoredResponseRecord> existing = findByFingerprint(fingerprint);
        if (existing.isPresent()) {
            return existing.get();
        }
        StructuredIncidentRecordProjector.Projection structured =
                structuredProjector.project(analyses, confirmations);
        for (int attempt = 0; attempt < MAX_ID_ATTEMPTS; attempt++) {
            String recordId = idGenerator.nextId();
            OffsetDateTime savedAt = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
            try {
                StoredResponseRecord saved = transactionTemplate.execute(status -> {
                    Optional<StoredResponseRecord> concurrent = findByFingerprint(fingerprint);
                    if (concurrent.isPresent()) {
                        return concurrent.get();
                    }
                    insertRecord(recordId, incidentId, request, fingerprint, requestId,
                            principal, savedAt, agentMemory);
                    insertMessages(recordId, request.messages());
                    insertAnalyses(recordId, analyses);
                    insertConfirmations(recordId, confirmations);
                    insertMovement(recordId, movementContext, movementState);
                    insertStructuredOutcome(recordId, incidentId,
                            request.outcomeReport(), structured, savedAt);
                    return new StoredResponseRecord(recordId, incidentId, fingerprint,
                            request.conversationStartedAt(), principal.userId(),
                            principal.organizationId(), requestId, savedAt,
                            agentMemory.map(IncidentAgentMemoryStore.StoredMemory::revision)
                                    .orElse(null),
                            agentMemory.map(IncidentAgentMemoryStore.StoredMemory::memorySha256)
                                    .orElse(null));
                });
                if (saved == null) {
                    throw new IllegalStateException("record transaction returned no value");
                }
                return saved;
            } catch (DuplicateKeyException collision) {
                Optional<StoredResponseRecord> idempotent = findByFingerprint(fingerprint);
                if (idempotent.isPresent()) {
                    return idempotent.get();
                }
            }
        }
        throw new IllegalStateException("고유한 record ID를 생성하지 못했습니다.");
    }

    private void ensureIncident(String incidentId) {
        OffsetDateTime activityAt = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        Integer updated = transactionTemplate.execute(status -> jdbcTemplate.update("""
                        UPDATE incidents SET last_activity_at = ? WHERE incident_id = ?
                        """, activityAt, incidentId));
        if (updated != null && updated == 1) {
            return;
        }
        try {
            transactionTemplate.executeWithoutResult(status -> jdbcTemplate.update("""
                            INSERT INTO incidents (
                                incident_id, created_at, last_activity_at
                            ) VALUES (?, ?, ?)
                            """, incidentId, activityAt, activityAt));
        } catch (DuplicateKeyException concurrentInsert) {
            Integer concurrentUpdate = transactionTemplate.execute(status ->
                    jdbcTemplate.update("""
                                    UPDATE incidents SET last_activity_at = ?
                                    WHERE incident_id = ?
                                    """, activityAt, incidentId));
            if (concurrentUpdate == null || concurrentUpdate != 1) {
                throw concurrentInsert;
            }
        }
    }

    public Optional<StoredResponseRecord> findById(String recordId) {
        return query("SELECT * FROM response_records WHERE record_id = ?", recordId);
    }

    public int messageCount(String recordId) {
        Integer count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM response_record_messages WHERE record_id = ?
                        """, Integer.class, recordId);
        return count == null ? 0 : count;
    }

    private Optional<StoredResponseRecord> findByFingerprint(String fingerprint) {
        return query("SELECT * FROM response_records WHERE record_fingerprint = ?",
                fingerprint);
    }

    private Optional<StoredResponseRecord> query(String sql, String value) {
        return jdbcTemplate.query(sql, (resultSet, rowNumber) -> new StoredResponseRecord(
                resultSet.getString("record_id"),
                resultSet.getString("incident_id"),
                resultSet.getString("record_fingerprint"),
                resultSet.getObject("conversation_started_at", OffsetDateTime.class),
                resultSet.getString("saved_by_user_id"),
                resultSet.getString("saved_by_organization_id"),
                resultSet.getString("saved_request_id"),
                resultSet.getObject("saved_at", OffsetDateTime.class),
                resultSet.getObject("agent_memory_revision") == null
                        ? null : resultSet.getInt("agent_memory_revision"),
                resultSet.getString("agent_memory_sha256")), value).stream().findFirst();
    }

    private void insertRecord(String recordId, String incidentId,
                              RecordSaveRequest request, String fingerprint,
                              String requestId, BffUserPrincipal principal,
                              OffsetDateTime savedAt,
                              Optional<IncidentAgentMemoryStore.StoredMemory> memory) {
        jdbcTemplate.update("""
                        INSERT INTO response_records (
                            record_id, incident_id, record_fingerprint,
                            conversation_started_at, saved_by_user_id,
                            saved_by_organization_id, saved_request_id, saved_at,
                            agent_memory_revision, agent_memory_sha256,
                            agent_memory_json, agent_events_json
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, recordId, incidentId, fingerprint,
                request.conversationStartedAt(), principal.userId(),
                principal.organizationId(), requestId, savedAt,
                memory.map(IncidentAgentMemoryStore.StoredMemory::revision).orElse(null),
                memory.map(IncidentAgentMemoryStore.StoredMemory::memorySha256).orElse(null),
                memory.map(value -> json(value.memory())).orElse(null),
                memory.map(value -> json(value.events())).orElse(null));
    }

    private void insertMessages(String recordId,
                                List<RecordSaveRequest.ConversationMessage> messages) {
        for (RecordSaveRequest.ConversationMessage message : messages) {
            jdbcTemplate.update("""
                            INSERT INTO response_record_messages (
                                record_id, message_id, message_sequence, message_role,
                                message_text, created_at, analysis_id
                            ) VALUES (?, ?, ?, ?, ?, ?, ?)
                            """, recordId, message.messageId(), message.sequence(),
                    message.role().name(), message.text(), message.createdAt(),
                    message.analysisId());
        }
    }

    private void insertAnalyses(String recordId,
                                List<IncidentAnalysisSnapshotStore.Snapshot> analyses) {
        for (int index = 0; index < analyses.size(); index++) {
            jdbcTemplate.update("""
                            INSERT INTO response_record_analyses (
                                record_id, analysis_id, reference_order
                            ) VALUES (?, ?, ?)
                            """, recordId, analyses.get(index).analysisId(), index + 1);
        }
    }

    private void insertConfirmations(String recordId,
                                     List<SubstanceConfirmation> confirmations) {
        for (int index = 0; index < confirmations.size(); index++) {
            jdbcTemplate.update("""
                            INSERT INTO response_record_confirmations (
                                record_id, confirmation_id, reference_order
                            ) VALUES (?, ?, ?)
                            """, recordId, confirmations.get(index).confirmationId(),
                    index + 1);
        }
    }

    private void insertMovement(
            String recordId,
            Optional<IncidentMovementContextStore.IncidentContext> context,
            Optional<MovementStateStore.MovementState> state) {
        if (context.isEmpty() && state.isEmpty()) {
            return;
        }
        jdbcTemplate.update("""
                        INSERT INTO response_record_movement_snapshots (
                            record_id, incident_context_json, movement_state_json
                        ) VALUES (?, ?, ?)
                        """, recordId, context.map(this::json).orElse(null),
                state.map(this::json).orElse(null));
    }

    private void insertStructuredOutcome(
            String recordId,
            String incidentId,
            StructuredIncidentOutcome outcome,
            StructuredIncidentRecordProjector.Projection structured,
            OffsetDateTime structuredAt) {
        jdbcTemplate.update("""
                        INSERT INTO incident_response_summaries (
                            record_id, incident_id, facility_name, facility_address,
                            incident_substance_name, incident_substance_cas,
                            brief_application_status, final_response_outcome,
                            structured_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, recordId, incidentId, outcome.facilityName().trim(),
                blankToNull(outcome.facilityAddress()),
                structured.incidentSubstanceName(), structured.incidentSubstanceCas(),
                outcome.briefApplicationStatus().name(),
                outcome.finalResponseOutcome().name(), structuredAt);
        for (int index = 0; index < outcome.performedActions().size(); index++) {
            jdbcTemplate.update("""
                            INSERT INTO incident_response_actions (
                                record_id, action_code, action_order
                            ) VALUES (?, ?, ?)
                            """, recordId, outcome.performedActions().get(index).name(),
                    index + 1);
        }
        for (int index = 0; index < outcome.additionalFactors().size(); index++) {
            jdbcTemplate.update("""
                            INSERT INTO incident_additional_factors (
                                record_id, factor_code, factor_order
                            ) VALUES (?, ?, ?)
                            """, recordId, outcome.additionalFactors().get(index).name(),
                    index + 1);
        }
        insertConflictRisk(recordId, structured.conflictRisk());
    }

    private void insertConflictRisk(
            String recordId,
            StructuredIncidentRecordProjector.ConflictRisk risk) {
        if (risk == null) return;
        jdbcTemplate.update("""
                        INSERT INTO incident_conflict_risks (
                            record_id, analysis_id, incident_cas,
                            facility_substance_name, facility_substance_cas,
                            rule_id, rule_version, severity, risk_level,
                            risk_level_ko, brief_text, expert_reviewed,
                            human_confirmation_required
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, recordId, risk.analysisId(), risk.incidentCas(),
                risk.facilitySubstanceName(), risk.facilitySubstanceCas(),
                risk.ruleId(), risk.ruleVersion(), risk.severity(), risk.riskLevel(),
                risk.riskLevelKo(), risk.briefText(), risk.expertReviewed(),
                risk.humanConfirmationRequired());
        for (int index = 0; index < risk.hazardCodes().size(); index++) {
            jdbcTemplate.update("""
                            INSERT INTO incident_conflict_hazards (
                                record_id, hazard_code, hazard_order
                            ) VALUES (?, ?, ?)
                            """, recordId, risk.hazardCodes().get(index), index + 1);
        }
        for (int index = 0; index < risk.gasProducts().size(); index++) {
            jdbcTemplate.update("""
                            INSERT INTO incident_conflict_gas_products (
                                record_id, gas_product, product_order
                            ) VALUES (?, ?, ?)
                            """, recordId, risk.gasProducts().get(index), index + 1);
        }
    }

    private String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("record snapshot을 직렬화하지 못했습니다.", error);
        }
    }
}
