package com.c2guard.bff.incident;

import com.c2guard.bff.common.BffContractException;
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
        analysisRequestMapper.addActiveConfirmations(prepared.modelRequest(), activeConfirmations);

        long revision = revisionStore.nextRevision(prepared.incidentId());
        ObjectNode briefRequest = briefRequestMapper.map(prepared.modelRequest(), revision,
                command.invalidatedConfirmationIds(), command.reportedEvidenceConflict());

        ModelApiResponse response = modelApiClient.briefIncident(briefRequest, requestId);
        if (!requestId.equals(response.requestId())) {
            throw new BffContractException(422, "MODEL_CONTRACT_VIOLATION",
                    "Model API client의 request ID가 인입 요청과 다릅니다.", false);
        }
        return response.body();
    }
}
