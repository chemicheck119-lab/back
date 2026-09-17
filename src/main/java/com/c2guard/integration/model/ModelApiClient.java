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

    ModelApiResponse stepIncidentAgent(JsonNode request, String requestId);

    /**
     * 신규 행동 카드 API. {@code analysis}로 감싼 기존 IncidentAnalyzeRequest와
     * Backend가 관리하는 {@code revision}을 받아 {@code action-brief-v1} 응답을 반환한다.
     * 기존 {@link #analyzeIncident} schema({@code chemiguard119-api-v1})와는 별도로 검증한다.
     */
    ModelApiResponse briefIncident(JsonNode request, String requestId);
}
