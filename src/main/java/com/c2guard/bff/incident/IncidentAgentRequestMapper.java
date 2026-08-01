package com.c2guard.bff.incident;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
class IncidentAgentRequestMapper {

    private static final int MAX_ACTIONS = 6;

    private final ObjectMapper objectMapper;

    IncidentAgentRequestMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    ObjectNode map(ObjectNode analysis,
                   Optional<IncidentAgentMemoryStore.StoredMemory> storedMemory) {
        ObjectNode target = objectMapper.createObjectNode();
        target.set("analysis", analysis.deepCopy());
        storedMemory.ifPresent(value -> target.set("memory", value.memory().deepCopy()));
        target.put("max_actions", MAX_ACTIONS);
        return target;
    }
}
