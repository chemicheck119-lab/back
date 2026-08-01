package com.c2guard.bff.incident;

import com.c2guard.bff.common.BffContractException;
import com.c2guard.integration.model.ModelApiClient;
import com.c2guard.integration.model.ModelApiResponse;
import com.c2guard.security.BffUserPrincipal;
import com.c2guard.security.IncidentAccessPolicy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

@Service
public class IncidentAnalysisBffService {

    private final ModelApiClient modelApiClient;
    private final IncidentAnalysisRequestMapper requestMapper;
    private final IncidentAnalysisProjector projector;
    private final IncidentAnalysisSnapshotStore snapshotStore;
    private final IncidentAccessPolicy incidentAccessPolicy;

    public IncidentAnalysisBffService(ModelApiClient modelApiClient,
                                      IncidentAnalysisRequestMapper requestMapper,
                                      IncidentAnalysisProjector projector,
                                      IncidentAnalysisSnapshotStore snapshotStore,
                                      IncidentAccessPolicy incidentAccessPolicy) {
        this.modelApiClient = modelApiClient;
        this.requestMapper = requestMapper;
        this.projector = projector;
        this.snapshotStore = snapshotStore;
        this.incidentAccessPolicy = incidentAccessPolicy;
    }

    public JsonNode analyze(IncidentAnalyzeRequest request, String requestId,
                            BffUserPrincipal principal) {
        incidentAccessPolicy.requireAnalyze(principal, request.incidentId());
        PreparedIncidentAnalysis prepared = requestMapper.prepare(request, requestId);
        ModelApiResponse modelResponse = modelApiClient.analyzeIncident(
                prepared.modelRequest(), requestId);
        if (!requestId.equals(modelResponse.requestId())) {
            throw new BffContractException(422, "MODEL_CONTRACT_VIOLATION",
                    "Model API client의 request ID가 인입 요청과 다릅니다.", false);
        }
        ObjectNode bffResponse = projector.project(modelResponse.body(), requestId,
                prepared.incidentId());
        snapshotStore.save(prepared.incidentId(), bffResponse.path("analysisId").asText(),
                requestId, modelResponse.body(), bffResponse);
        return bffResponse;
    }
}
