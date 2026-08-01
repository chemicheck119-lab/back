package com.c2guard.integration.model;

import com.fasterxml.jackson.databind.JsonNode;

public interface ModelApiClient {

    ModelApiResponse live(String requestId);

    ModelApiResponse ready(String requestId);

    ModelApiResponse metadata(String requestId);

    ModelApiResponse resolveSubstances(JsonNode request, String requestId);

    ModelApiResponse discoverSubstances(JsonNode request, String requestId);

    ModelApiResponse searchEvidence(JsonNode request, String requestId);

    ModelApiResponse findFacilityCandidates(JsonNode request, String requestId);

    ModelApiResponse reviewConflicts(JsonNode request, String requestId);

    ModelApiResponse analyzeIncident(JsonNode request, String requestId);
}
