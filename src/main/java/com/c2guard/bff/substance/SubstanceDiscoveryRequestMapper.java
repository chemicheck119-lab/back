package com.c2guard.bff.substance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

@Component
class SubstanceDiscoveryRequestMapper {

    private final ObjectMapper objectMapper;

    SubstanceDiscoveryRequestMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    ObjectNode map(SubstanceDiscoveryRequest source) {
        ObjectNode target = objectMapper.createObjectNode();
        target.put("query", source.query().trim());
        target.put("top_k", source.topK());
        target.put("evidence_top_k", source.evidenceTopK());
        return target;
    }
}
