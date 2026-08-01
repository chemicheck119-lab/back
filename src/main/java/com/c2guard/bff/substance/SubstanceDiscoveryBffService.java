package com.c2guard.bff.substance;

import com.c2guard.bff.common.BffContractException;
import com.c2guard.integration.model.ModelApiClient;
import com.c2guard.integration.model.ModelApiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

@Service
public class SubstanceDiscoveryBffService {

    private final ModelApiClient modelApiClient;
    private final SubstanceDiscoveryRequestMapper requestMapper;
    private final SubstanceDiscoveryProjector projector;

    public SubstanceDiscoveryBffService(ModelApiClient modelApiClient,
                                        SubstanceDiscoveryRequestMapper requestMapper,
                                        SubstanceDiscoveryProjector projector) {
        this.modelApiClient = modelApiClient;
        this.requestMapper = requestMapper;
        this.projector = projector;
    }

    public JsonNode discover(SubstanceDiscoveryRequest request, String requestId) {
        ObjectNode modelRequest = requestMapper.map(request);
        ModelApiResponse modelResponse = modelApiClient.discoverSubstances(modelRequest, requestId);
        if (!requestId.equals(modelResponse.requestId())) {
            throw new BffContractException(422, "MODEL_CONTRACT_VIOLATION",
                    "Model API client의 request ID가 인입 요청과 다릅니다.", false);
        }
        return projector.project(modelResponse.body(), requestId,
                modelRequest.path("query").asText());
    }
}
