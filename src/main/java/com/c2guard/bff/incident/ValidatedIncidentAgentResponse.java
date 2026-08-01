package com.c2guard.bff.incident;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

record ValidatedIncidentAgentResponse(
        String runId,
        String status,
        boolean retryable,
        JsonNode analysis,
        ObjectNode memory,
        ArrayNode events
) {
}
