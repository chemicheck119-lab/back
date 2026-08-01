package com.c2guard.bff.record;

import com.c2guard.bff.common.BffContractException;
import com.c2guard.bff.confirmation.ConfirmationStore;
import com.c2guard.bff.confirmation.SubstanceConfirmation;
import com.c2guard.bff.incident.IncidentAgentMemoryStore;
import com.c2guard.bff.incident.IncidentAnalysisSnapshotStore;
import com.c2guard.bff.movement.IncidentMovementContextStore;
import com.c2guard.bff.movement.MovementStateStore;
import com.c2guard.security.BffUserPrincipal;
import com.c2guard.security.IncidentAccessPolicy;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class RecordSaveService {

    private static final Duration MAX_FUTURE_SKEW = Duration.ofMinutes(5);

    private final ResponseRecordStore recordStore;
    private final IncidentAnalysisSnapshotStore analysisStore;
    private final ConfirmationStore confirmationStore;
    private final IncidentAgentMemoryStore memoryStore;
    private final IncidentMovementContextStore movementContextStore;
    private final MovementStateStore movementStateStore;
    private final IncidentAccessPolicy incidentAccessPolicy;
    private final RecordFingerprint fingerprint;
    private final Clock clock;

    public RecordSaveService(ResponseRecordStore recordStore,
                             IncidentAnalysisSnapshotStore analysisStore,
                             ConfirmationStore confirmationStore,
                             IncidentAgentMemoryStore memoryStore,
                             IncidentMovementContextStore movementContextStore,
                             MovementStateStore movementStateStore,
                             IncidentAccessPolicy incidentAccessPolicy,
                             RecordFingerprint fingerprint,
                             Clock clock) {
        this.recordStore = recordStore;
        this.analysisStore = analysisStore;
        this.confirmationStore = confirmationStore;
        this.memoryStore = memoryStore;
        this.movementContextStore = movementContextStore;
        this.movementStateStore = movementStateStore;
        this.incidentAccessPolicy = incidentAccessPolicy;
        this.fingerprint = fingerprint;
        this.clock = clock;
    }

    public RecordSaveResponse save(String incidentId, RecordSaveRequest request,
                                   String requestId, BffUserPrincipal principal) {
        incidentAccessPolicy.requireAccess(principal, incidentId);
        validateConversation(request);
        validateMessageReferences(request, request.analysisIds());
        try {
            List<IncidentAnalysisSnapshotStore.Snapshot> analyses =
                    validateAnalyses(incidentId, request.analysisIds());
            List<SubstanceConfirmation> confirmations =
                    validateConfirmations(incidentId, request.confirmationIds());
            StoredResponseRecord stored = recordStore.save(incidentId, request,
                    fingerprint.calculate(incidentId, request, principal), requestId,
                    principal, analyses, confirmations, memoryStore.find(incidentId),
                    movementContextStore.find(incidentId),
                    movementStateStore.find(incidentId));
            return new RecordSaveResponse(requestId, incidentId, stored.recordId(),
                    stored.savedAt());
        } catch (DataAccessException error) {
            throw new BffContractException(503, "RECORD_SAVE_FAILED",
                    "대응 기록을 저장하지 못했습니다. 현재 화면을 유지하고 다시 시도하세요.",
                    true);
        }
    }

    private void validateConversation(RecordSaveRequest request) {
        OffsetDateTime maximum = OffsetDateTime.ofInstant(
                clock.instant().plus(MAX_FUTURE_SKEW), request.conversationStartedAt().getOffset());
        if (request.conversationStartedAt().isAfter(maximum)) {
            throw new IllegalArgumentException("conversationStartedAt이 미래입니다.");
        }
        Set<String> messageIds = new HashSet<>();
        Set<Integer> sequences = new HashSet<>();
        OffsetDateTime previous = request.conversationStartedAt();
        for (int index = 0; index < request.messages().size(); index++) {
            RecordSaveRequest.ConversationMessage message = request.messages().get(index);
            if (!messageIds.add(message.messageId()) || !sequences.add(message.sequence())
                    || message.sequence() != index + 1) {
                throw new IllegalArgumentException("메시지 ID와 sequence는 중복 없이 1부터 순서대로여야 합니다.");
            }
            if (message.createdAt().isBefore(request.conversationStartedAt())
                    || message.createdAt().isBefore(previous)
                    || message.createdAt().toInstant().isAfter(
                    clock.instant().plus(MAX_FUTURE_SKEW))) {
                throw new IllegalArgumentException("메시지 시각 순서가 올바르지 않습니다.");
            }
            previous = message.createdAt();
        }
    }

    private List<IncidentAnalysisSnapshotStore.Snapshot> validateAnalyses(
            String incidentId, List<String> analysisIds) {
        if (new HashSet<>(analysisIds).size() != analysisIds.size()) {
            throw referenceConflict();
        }
        List<IncidentAnalysisSnapshotStore.Snapshot> analyses = new ArrayList<>();
        for (String analysisId : analysisIds) {
            IncidentAnalysisSnapshotStore.Snapshot analysis = analysisStore.find(analysisId)
                    .orElseThrow(RecordSaveService::referenceConflict);
            if (!incidentId.equals(analysis.incidentId())) {
                throw referenceConflict();
            }
            analyses.add(analysis);
        }
        return List.copyOf(analyses);
    }

    private List<SubstanceConfirmation> validateConfirmations(
            String incidentId, List<String> confirmationIds) {
        if (new HashSet<>(confirmationIds).size() != confirmationIds.size()) {
            throw referenceConflict();
        }
        List<SubstanceConfirmation> confirmations = new ArrayList<>();
        for (String confirmationId : confirmationIds) {
            SubstanceConfirmation confirmation = confirmationStore.findById(confirmationId)
                    .orElseThrow(RecordSaveService::referenceConflict);
            if (!incidentId.equals(confirmation.incidentId())) {
                throw referenceConflict();
            }
            confirmations.add(confirmation);
        }
        return List.copyOf(confirmations);
    }

    private void validateMessageReferences(RecordSaveRequest request,
                                           List<String> analysisIds) {
        Set<String> allowed = Set.copyOf(analysisIds);
        for (RecordSaveRequest.ConversationMessage message : request.messages()) {
            if (message.analysisId() != null && !allowed.contains(message.analysisId())) {
                throw referenceConflict();
            }
        }
    }

    private static BffContractException referenceConflict() {
        return new BffContractException(409, "INCIDENT_REFERENCE_CONFLICT",
                "분석 또는 확인 ID가 없거나 현재 사고와 일치하지 않습니다.", false);
    }
}
