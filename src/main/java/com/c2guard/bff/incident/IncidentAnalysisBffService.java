package com.c2guard.bff.incident;

import com.c2guard.bff.common.BffContractException;
import com.c2guard.bff.confirmation.ConfirmationRole;
import com.c2guard.bff.confirmation.ConfirmationStore;
import com.c2guard.bff.confirmation.SubstanceConfirmation;
import com.c2guard.bff.movement.IncidentMovementContextStore;
import com.c2guard.integration.model.ModelApiClient;
import com.c2guard.integration.model.ModelApiResponse;
import com.c2guard.security.BffUserPrincipal;
import com.c2guard.security.IncidentAccessPolicy;
import com.c2guard.bff.phone.PhoneTranscriptEventBroker;
import com.c2guard.bff.phone.PhoneTranscriptReviewStatus;
import com.c2guard.bff.phone.PhoneTranscriptStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
public class IncidentAnalysisBffService {

    private final ModelApiClient modelApiClient;
    private final IncidentAnalysisRequestMapper requestMapper;
    private final IncidentAnalysisProjector projector;
    private final IncidentAnalysisSnapshotStore snapshotStore;
    private final IncidentAccessPolicy incidentAccessPolicy;
    private final ConfirmationStore confirmationStore;
    private final IncidentAgentMemoryStore agentMemoryStore;
    private final IncidentAgentRequestMapper agentRequestMapper;
    private final IncidentAgentResponseValidator agentResponseValidator;
    private final IncidentMovementContextStore movementContextStore;
    private final PhoneTranscriptStore phoneTranscriptStore;
    private final PhoneTranscriptEventBroker phoneTranscriptEventBroker;
    private final Clock clock;

    public IncidentAnalysisBffService(ModelApiClient modelApiClient,
                                      IncidentAnalysisRequestMapper requestMapper,
                                      IncidentAnalysisProjector projector,
                                      IncidentAnalysisSnapshotStore snapshotStore,
                                      IncidentAccessPolicy incidentAccessPolicy,
                                      ConfirmationStore confirmationStore,
                                      IncidentAgentMemoryStore agentMemoryStore,
                                      IncidentAgentRequestMapper agentRequestMapper,
                                      IncidentAgentResponseValidator agentResponseValidator,
                                      IncidentMovementContextStore movementContextStore,
                                      PhoneTranscriptStore phoneTranscriptStore,
                                      PhoneTranscriptEventBroker phoneTranscriptEventBroker,
                                      Clock clock) {
        this.modelApiClient = modelApiClient;
        this.requestMapper = requestMapper;
        this.projector = projector;
        this.snapshotStore = snapshotStore;
        this.incidentAccessPolicy = incidentAccessPolicy;
        this.confirmationStore = confirmationStore;
        this.agentMemoryStore = agentMemoryStore;
        this.agentRequestMapper = agentRequestMapper;
        this.agentResponseValidator = agentResponseValidator;
        this.movementContextStore = movementContextStore;
        this.phoneTranscriptStore = phoneTranscriptStore;
        this.phoneTranscriptEventBroker = phoneTranscriptEventBroker;
        this.clock = clock;
    }

    public JsonNode analyze(IncidentAnalyzeRequest request, String requestId,
                            BffUserPrincipal principal) {
        incidentAccessPolicy.requireAnalyze(principal, request.incidentId());
        PhoneTranscriptStore.StoredTranscript reviewedTranscript =
                requireReviewedPhoneTranscript(request);
        PreparedIncidentAnalysis prepared = requestMapper.prepare(request, requestId);
        movementContextStore.capture(prepared.incidentId(), request);
        Map<ConfirmationRole, SubstanceConfirmation> activeConfirmations =
                confirmationStore.findActiveForIncident(prepared.incidentId());
        requestMapper.addActiveConfirmations(prepared.modelRequest(),
                activeConfirmations);
        ObjectNode agentRequest = agentRequestMapper.map(prepared.modelRequest(),
                agentMemoryStore.find(prepared.incidentId()));
        ModelApiResponse modelResponse = modelApiClient.stepIncidentAgent(
                agentRequest, requestId);
        if (!requestId.equals(modelResponse.requestId())) {
            throw new BffContractException(422, "MODEL_CONTRACT_VIOLATION",
                    "Model API client의 request ID가 인입 요청과 다릅니다.", false);
        }
        ValidatedIncidentAgentResponse agentResponse = agentResponseValidator.validate(
                modelResponse.body(), requestId, prepared.incidentId());
        ensureConfirmationStateUnchanged(prepared.incidentId(), activeConfirmations);

        ObjectNode bffResponse = projectOrLoadLatest(agentResponse, requestId,
                prepared.incidentId(), activeConfirmations);
        agentMemoryStore.compareAndSet(prepared.incidentId(), agentResponse.memory(),
                agentResponse.events(), requestId, agentResponse.runId());

        if ("FAILED_SAFETY".equals(agentResponse.status())) {
            throw new BffContractException(500, "AGENT_SAFETY_FAILURE",
                    "사고 에이전트 안전 검증에 실패해 결과를 표시하지 않습니다.", false);
        }
        if ("FAILED_RETRYABLE".equals(agentResponse.status())) {
            throw new BffContractException(503, "AGENT_EXECUTION_FAILED",
                    "사고 에이전트 도구 실행에 실패했습니다. 현재 화면을 유지하고 다시 시도하세요.",
                    agentResponse.retryable());
        }

        if (agentResponse.analysis() != null) {
            snapshotStore.save(prepared.incidentId(), bffResponse.path("analysisId").asText(),
                    requestId, agentResponse.analysis(), bffResponse, modelResponse.body(),
                    activeConfirmations);
        }
        if (reviewedTranscript != null) {
            PhoneTranscriptStore.StoredTranscript analyzed = phoneTranscriptStore.markAnalyzed(
                    prepared.incidentId(), reviewedTranscript.transcriptId(),
                    reviewedTranscript.revision(), bffResponse.path("analysisId").asText(),
                    requestId, OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
            phoneTranscriptEventBroker.publish(analyzed);
        }
        return bffResponse;
    }

    private PhoneTranscriptStore.StoredTranscript requireReviewedPhoneTranscript(
            IncidentAnalyzeRequest request) {
        if (request.inputType() != IncidentAnalyzeRequest.InputType.PHONE_TRANSCRIPT) {
            return null;
        }
        PhoneTranscriptStore.StoredTranscript stored = phoneTranscriptStore
                .findById(request.incidentId(), request.phoneTranscriptId())
                .orElseThrow(() -> new BffContractException(404,
                        "PHONE_TRANSCRIPT_NOT_FOUND", "분석할 전화 전사를 찾을 수 없습니다.", false));
        if (stored.revision() != request.phoneTranscriptRevision()) {
            throw new BffContractException(409, "PHONE_TRANSCRIPT_REVISION_CONFLICT",
                    "전화 전사 revision이 변경되었습니다. 최신 검토본을 사용하세요.", true);
        }
        if (!PhoneTranscriptReviewStatus.REVIEWED.name().equals(stored.reviewStatus())
                && !PhoneTranscriptReviewStatus.ANALYZED.name().equals(stored.reviewStatus())) {
            throw new BffContractException(409, "PHONE_TRANSCRIPT_REVIEW_REQUIRED",
                    "담당자가 승인한 최종 전화 전사만 분석할 수 있습니다.", false);
        }
        if (!stored.text().trim().equals(request.text().trim())) {
            throw new BffContractException(409, "PHONE_TRANSCRIPT_TEXT_MISMATCH",
                    "분석 입력이 서버의 승인된 전사와 다릅니다. 다시 승인하세요.", false);
        }
        return stored;
    }

    private ObjectNode projectOrLoadLatest(ValidatedIncidentAgentResponse agentResponse,
                                           String requestId, String incidentId,
                                           Map<ConfirmationRole, SubstanceConfirmation>
                                                   activeConfirmations) {
        if (agentResponse.analysis() != null) {
            return projector.project(agentResponse.analysis(), requestId, incidentId);
        }
        if (agentResponse.status().startsWith("FAILED")) {
            return null;
        }
        IncidentAnalysisSnapshotStore.Snapshot snapshot = snapshotStore
                .findLatestForIncident(incidentId)
                .orElseThrow(() -> new BffContractException(422,
                        "MODEL_CONTRACT_VIOLATION",
                        "agent가 분석 없이 응답했지만 이전 권위 분석이 없습니다.", false));
        if (!snapshot.matchesConfirmations(activeConfirmations)) {
            throw new BffContractException(422, "MODEL_CONTRACT_VIOLATION",
                    "confirmation 변경 뒤 새 분석 없이 과거 결과를 재사용할 수 없습니다.",
                    false);
        }
        ObjectNode current = (ObjectNode) snapshot.bffResponse().deepCopy();
        current.put("requestId", requestId);
        return current;
    }

    private void ensureConfirmationStateUnchanged(
            String incidentId,
            Map<ConfirmationRole, SubstanceConfirmation> expected) {
        Map<ConfirmationRole, SubstanceConfirmation> current =
                confirmationStore.findActiveForIncident(incidentId);
        for (ConfirmationRole role : ConfirmationRole.values()) {
            String expectedId = confirmationId(expected.get(role));
            String currentId = confirmationId(current.get(role));
            if (!Objects.equals(expectedId, currentId)) {
                throw new BffContractException(409, "INCIDENT_REFERENCE_CONFLICT",
                        "분석 중 confirmation이 변경됐습니다. 최신 상태로 다시 분석하세요.",
                        true);
            }
        }
    }

    private String confirmationId(SubstanceConfirmation confirmation) {
        return confirmation == null ? null : confirmation.confirmationId();
    }
}
