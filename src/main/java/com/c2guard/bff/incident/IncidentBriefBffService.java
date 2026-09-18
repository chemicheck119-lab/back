package com.c2guard.bff.incident;

import com.c2guard.bff.common.BffContractException;
import com.c2guard.bff.confirmation.CasNumberValidator;
import com.c2guard.bff.confirmation.ConfirmationRole;
import com.c2guard.bff.confirmation.ConfirmationStore;
import com.c2guard.bff.confirmation.SubstanceConfirmation;
import com.c2guard.integration.model.ModelApiClient;
import com.c2guard.integration.model.ModelApiResponse;
import com.c2guard.security.BffUserPrincipal;
import com.c2guard.security.IncidentAccessPolicy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;

/**
 * 신규 행동 카드({@code action-brief-v1}) 모델 API 연동.
 * <p>
 * {@link IncidentAnalysisBffService}가 쓰는 analysis·confirmation 조립 로직을 그대로
 * 재사용하되, 응답 schema·revision 관리는 분리한다 — 기존 분석 경로를 덮어쓰지 않는다
 * (docs(api) 백엔드 연동 가이드 07항 "schema 분리").
 * <p>
 * 아직 프론트에 노출하는 공개 컨트롤러 경로/응답 DTO는 정해지지 않아 이 서비스는
 * 모델 API 원본 응답({@code action-brief-v1} JSON)을 그대로 반환한다.
 */
@Service
public class IncidentBriefBffService {

    private final ModelApiClient modelApiClient;
    private final IncidentAnalysisRequestMapper analysisRequestMapper;
    private final IncidentBriefRequestMapper briefRequestMapper;
    private final IncidentBriefRevisionStore revisionStore;
    private final ConfirmationStore confirmationStore;
    private final IncidentAccessPolicy incidentAccessPolicy;

    public IncidentBriefBffService(ModelApiClient modelApiClient,
                                   IncidentAnalysisRequestMapper analysisRequestMapper,
                                   IncidentBriefRequestMapper briefRequestMapper,
                                   IncidentBriefRevisionStore revisionStore,
                                   ConfirmationStore confirmationStore,
                                   IncidentAccessPolicy incidentAccessPolicy) {
        this.modelApiClient = modelApiClient;
        this.analysisRequestMapper = analysisRequestMapper;
        this.briefRequestMapper = briefRequestMapper;
        this.revisionStore = revisionStore;
        this.confirmationStore = confirmationStore;
        this.incidentAccessPolicy = incidentAccessPolicy;
    }

    public JsonNode brief(IncidentBriefCommand command, String requestId,
                          BffUserPrincipal principal) {
        IncidentAnalyzeRequest analysis = command.analysis();
        incidentAccessPolicy.requireAnalyze(principal, analysis.incidentId());

        PreparedIncidentAnalysis prepared = analysisRequestMapper.prepare(analysis, requestId);
        Map<ConfirmationRole, SubstanceConfirmation> activeConfirmations =
                confirmationStore.findActiveForIncident(prepared.incidentId());
        requireConfirmedCasPair(activeConfirmations);
        analysisRequestMapper.addActiveConfirmations(prepared.modelRequest(), activeConfirmations);

        long revision = revisionStore.nextRevision(prepared.incidentId());
        ObjectNode briefRequest = briefRequestMapper.map(prepared.modelRequest(), revision,
                command.invalidatedConfirmationIds(), command.reportedEvidenceConflict());

        ModelApiResponse response = modelApiClient.briefIncident(briefRequest, requestId);
        if (!requestId.equals(response.requestId())) {
            throw new BffContractException(422, "MODEL_CONTRACT_VIOLATION",
                    "Model API client의 request ID가 인입 요청과 다릅니다.", false);
        }
        ensureConfirmationStateUnchanged(prepared.incidentId(), activeConfirmations);
        return response.body();
    }

    private void requireConfirmedCasPair(
            Map<ConfirmationRole, SubstanceConfirmation> activeConfirmations) {
        for (ConfirmationRole role : ConfirmationRole.values()) {
            SubstanceConfirmation confirmation = activeConfirmations.get(role);
            if (confirmation == null
                    || confirmation.status() != com.c2guard.bff.confirmation.ConfirmationStatus.ACTIVE
                    || !CasNumberValidator.isValid(confirmation.casNumber())) {
                throw new BffContractException(409, "CONFIRMATION_REQUIRED",
                        "사고 물질과 시설 물질의 CAS 확인이 모두 완료되어야 브리핑을 요청할 수 있습니다.",
                        true);
            }
        }
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
                        "브리핑 중 confirmation이 변경됐습니다. 최신 상태로 다시 요청하세요.",
                        true);
            }
        }
    }

    private String confirmationId(SubstanceConfirmation confirmation) {
        return confirmation == null ? null : confirmation.confirmationId();
    }
}
