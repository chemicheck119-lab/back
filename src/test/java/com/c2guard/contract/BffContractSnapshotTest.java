package com.c2guard.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BffContractSnapshotTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Path BFF_CONTRACT = Path.of("contracts/dashboard-bff-v1.openapi.json");
    private static final Path MODEL_CONTRACT = Path.of("contracts/upstream/model-api-v1.openapi.json");
    private static final Path SPEECH_CONTRACT =
            Path.of("contracts/upstream/speech-api-v1.openapi.json");
    private static final Path INTEGRATION_CONTRACT =
            Path.of("contracts/upstream/model-api-integration-v1.json");
    private static final Path INCIDENT_REPLAY_CONTRACT =
            Path.of("contracts/incident-intake-replay-v1.openapi.json");
    private static final Path SYNTHETIC_DEMO_LOG_CONTRACT =
            Path.of("contracts/synthetic-demo-logs-v1.openapi.json");

    @Test
    void bffContractPublishesAuthenticatedBackendOwnedRoutes() throws IOException {
        JsonNode contract = read(BFF_CONTRACT);
        JsonNode paths = contract.path("paths");

        Map<String, String> expected = Map.of(
                "/api/c2guard/v1/session", "get",
                "/api/c2guard/v1/logout", "post",
                "/api/c2guard/v1/transcriptions", "post",
                "/api/c2guard/v1/incidents/analyze", "post",
                "/api/c2guard/v1/substances/discover", "post",
                "/api/c2guard/v1/incidents/{incidentId}/confirmations", "post",
                "/api/c2guard/v1/incidents/{incidentId}/movement", "post",
                "/api/c2guard/v1/incidents/{incidentId}/record", "post",
                "/api/c2guard/v1/incidents/{incidentId}/transcriptions", "post");
        assertEquals(expected.keySet(), fieldNames(paths));

        expected.forEach((path, method) -> {
            JsonNode operation = paths.path(path).path(method);
            assertFalse(operation.isMissingNode(), path);
            assertTrue(operation.path("security").toString().contains("ServiceSession"), path);
            assertEquals("BE_Repository", operation.path("x-implementation-owner").asText(), path);
            assertFalse(operation.path("x-model-api-direct-browser-call-allowed").asBoolean(true), path);
        });
    }

    @Test
    void aiBackedRoutesUseTwoAndFifteenSecondTimeoutPolicy() throws IOException {
        JsonNode contract = read(BFF_CONTRACT);

        assertEquals(2, contract.path("x-model-api-connect-timeout-seconds").asInt());
        assertEquals(15, contract.path("x-model-api-response-timeout-seconds").asInt());
        assertTimeoutResponse(contract, "/api/c2guard/v1/incidents/analyze");
        assertTimeoutResponse(contract, "/api/c2guard/v1/substances/discover");
        assertSpeechTimeoutResponse(contract,
                "/api/c2guard/v1/incidents/{incidentId}/transcriptions");
        assertSpeechTimeoutResponse(contract, "/api/c2guard/v1/transcriptions");

        assertFalse(hasResponse(contract, "/api/c2guard/v1/incidents/{incidentId}/confirmations", "504"));
        assertFalse(hasResponse(contract, "/api/c2guard/v1/incidents/{incidentId}/movement", "504"));
        assertFalse(hasResponse(contract, "/api/c2guard/v1/incidents/{incidentId}/record", "504"));
    }

    @Test
    void speechContractPreservesUncertaintyAndForbidsDirectSafetyDecisions()
            throws IOException {
        JsonNode bff = read(BFF_CONTRACT);
        JsonNode operation = bff.path("paths")
                .path("/api/c2guard/v1/incidents/{incidentId}/transcriptions")
                .path("post");
        assertFalse(operation.path("x-speech-api-direct-browser-call-allowed")
                .asBoolean(true));
        assertFalse(operation.path("x-audio-retained").asBoolean(true));
        assertTrue(hasResponse(bff,
                "/api/c2guard/v1/incidents/{incidentId}/transcriptions", "413"));
        assertTrue(hasResponse(bff,
                "/api/c2guard/v1/incidents/{incidentId}/transcriptions", "429"));

        JsonNode response = bff.path("components").path("schemas")
                .path("DashboardSpeechTranscriptionResponse");
        assertTrue(response.path("properties").path("incidentId")
                .path("anyOf").toString().contains("null"));
        assertTrue(response.path("properties").path("requiresResponderReview")
                .path("const").asBoolean());
        JsonNode safety = bff.path("components").path("schemas")
                .path("DashboardSpeechSafetyBoundary").path("properties");
        assertFalse(safety.path("chemicalIdentificationPerformed").path("const")
                .asBoolean(true));
        assertFalse(safety.path("casConfirmationPerformed").path("const")
                .asBoolean(true));
        assertFalse(safety.path("riskAssessmentPerformed").path("const")
                .asBoolean(true));

        JsonNode upstream = read(SPEECH_CONTRACT);
        JsonNode transcription = upstream.path("paths")
                .path("/api/v1/transcriptions").path("post");
        assertFalse(transcription.isMissingNode());
        assertTrue(transcription.path("security").toString().contains("APIKeyHeader"));
        JsonNode upstreamSafety = upstream.path("components").path("schemas")
                .path("SafetyBoundaryResponse").path("properties");
        assertFalse(upstreamSafety.path("chemical_identification_performed")
                .path("const").asBoolean(true));
        assertFalse(upstreamSafety.path("cas_confirmation_performed")
                .path("const").asBoolean(true));
        assertFalse(upstreamSafety.path("risk_assessment_performed")
                .path("const").asBoolean(true));
    }

    @Test
    void errorEnvelopeCannotAllowResetOnFailure() throws IOException {
        JsonNode error = read(BFF_CONTRACT).path("components").path("schemas").path("DashboardErrorResponse");

        assertEquals(Set.of("requestId", "error"), jsonTextSet(error.path("required")));
        assertFalse(error.path("properties").path("resetAllowed").path("const").asBoolean(true));

        JsonNode detail = read(BFF_CONTRACT).path("components").path("schemas").path("DashboardErrorDetail");
        assertEquals(Set.of("code", "message", "retryable"), jsonTextSet(detail.path("required")));
    }

    @Test
    void recordContractRequiresCodedStructuredOutcomeFields() throws IOException {
        JsonNode schemas = read(BFF_CONTRACT).path("components").path("schemas");
        JsonNode request = schemas.path("DashboardRecordSaveRequest");
        assertTrue(jsonTextSet(request.path("required")).contains("outcomeReport"));
        assertEquals("#/components/schemas/DashboardStructuredIncidentOutcome",
                request.path("properties").path("outcomeReport").path("$ref").asText());

        JsonNode outcome = schemas.path("DashboardStructuredIncidentOutcome");
        assertEquals(Set.of("facilityName", "performedActions",
                        "briefApplicationStatus", "additionalFactors",
                        "finalResponseOutcome"),
                jsonTextSet(outcome.path("required")));
        assertTrue(outcome.path("properties").path("performedActions")
                .path("uniqueItems").asBoolean());
        assertTrue(outcome.path("properties").path("additionalFactors")
                .path("uniqueItems").asBoolean());

        JsonNode fixture = read(Path.of(
                "contracts/examples/bff/record_save_request.json"));
        assertEquals("APPLIED", fixture.path("outcomeReport")
                .path("briefApplicationStatus").asText());
        assertEquals("SPREAD_CONTAINED", fixture.path("outcomeReport")
                .path("finalResponseOutcome").asText());
    }

    @Test
    void publicIncidentReplayContractCannotBeMistakenForAuthorizedDispatch()
            throws IOException {
        JsonNode contract = read(INCIDENT_REPLAY_CONTRACT);
        JsonNode operation = contract.path("paths")
                .path("/api/c2guard/v1/intake/replay-stream/{scenarioId}")
                .path("get");
        JsonNode confirmationOperation = contract.path("paths")
                .path("/api/c2guard/v1/intake/replays/{incidentId}/confirmations/{role}")
                .path("post");

        assertEquals("chemicheck119-incident-intake-replay-v1",
                contract.path("x-contract-version").asText());
        assertEquals("PUBLIC_SYNTHETIC_ONLY",
                contract.path("x-data-boundary").asText());
        assertEquals("NOT_CONNECTED",
                contract.path("x-authorized-dispatch-status").asText());
        assertFalse(operation.path("x-official-119-integration").asBoolean(true));
        assertTrue(operation.path("x-public-only-when-configured").asBoolean());
        assertFalse(operation.path("x-production-default-enabled").asBoolean(true));
        assertTrue(operation.path("security").isArray());
        assertTrue(operation.path("security").isEmpty());
        assertEquals("#/components/schemas/IncidentEnvelope",
                operation.path("responses").path("200").path("content")
                        .path("text/event-stream").path("schema").path("$ref").asText());

        JsonNode envelope = contract.path("components").path("schemas")
                .path("IncidentEnvelope");
        assertEquals("SYNTHETIC_DISPATCH_REPLAY",
                envelope.path("properties").path("sourceType").path("const").asText());
        assertEquals("PUBLIC_SYNTHETIC",
                envelope.path("properties").path("dataClassification").path("const").asText());
        assertFalse(envelope.path("properties").path("containsPersonalInformation")
                .path("const").asBoolean(true));

        assertTrue(confirmationOperation.path("security").isEmpty());
        assertTrue(confirmationOperation.path("x-public-only-when-configured").asBoolean());
        assertFalse(confirmationOperation.path("x-production-default-enabled")
                .asBoolean(true));
        assertFalse(confirmationOperation.path("x-request-body-allowed").asBoolean(true));
        assertFalse(confirmationOperation.path("x-caller-selected-cas-allowed")
                .asBoolean(true));
        assertFalse(confirmationOperation.path("x-official-field-confirmation")
                .asBoolean(true));
        assertTrue(confirmationOperation.path("requestBody").isMissingNode());
        JsonNode syntheticConfirmation = contract.path("components").path("schemas")
                .path("SyntheticReplayConfirmation");
        assertEquals("PUBLIC_SYNTHETIC", syntheticConfirmation.path("properties")
                .path("dataClassification").path("const").asText());
        assertEquals("SYNTHETIC_DEMO_CONFIRMATION", syntheticConfirmation.path("properties")
                .path("confirmationType").path("const").asText());
        assertEquals(Set.of("7681-52-9", "7647-01-0"),
                jsonTextSet(syntheticConfirmation.path("properties")
                        .path("casNumber").path("enum")));

        JsonNode fixture = read(Path.of(
                "contracts/examples/bff/incident_replay_event.json"));
        assertEquals("PUBLIC_SYNTHETIC", fixture.path("dataClassification").asText());
        assertEquals("SYNTHETIC_DISPATCH_REPLAY", fixture.path("sourceType").asText());
        assertFalse(fixture.path("containsPersonalInformation").asBoolean(true));
        JsonNode confirmationFixture = read(Path.of(
                "contracts/examples/bff/synthetic_replay_confirmation.json"));
        assertEquals("PUBLIC_SYNTHETIC",
                confirmationFixture.path("dataClassification").asText());
        assertEquals("SYNTHETIC_DEMO_CONFIRMATION",
                confirmationFixture.path("confirmationType").asText());
    }

    @Test
    void nationwideDemoLogContractCannotBeMistakenForOperationalRecords()
            throws IOException {
        JsonNode contract = read(SYNTHETIC_DEMO_LOG_CONTRACT);
        assertEquals("chemicheck119-synthetic-demo-logs-v1",
                contract.path("x-contract-version").asText());
        assertEquals("PUBLIC_SYNTHETIC_ONLY",
                contract.path("x-data-boundary").asText());
        assertFalse(contract.path("x-official-119-integration").asBoolean(true));
        assertFalse(contract.path("x-production-default-enabled").asBoolean(true));

        for (String path : List.of("/api/c2guard/v1/demo/incident-logs",
                "/api/c2guard/v1/demo/incident-logs/coverage")) {
            JsonNode operation = contract.path("paths").path(path).path("get");
            assertEquals("PUBLIC_SYNTHETIC",
                    operation.path("x-data-classification").asText());
            assertFalse(operation.path("x-operational-record").asBoolean(true));
            assertTrue(operation.path("security").toString().contains("ServiceSession"));
        }

        JsonNode coverage = contract.path("components").path("schemas")
                .path("DemoIncidentLogCoverage").path("properties");
        assertEquals(17, coverage.path("regionCount").path("const").asInt());
        assertEquals(215, coverage.path("stationCount").path("const").asInt());
        assertEquals(3225, coverage.path("totalLogCount").path("const").asInt());
    }

    @Test
    void upstreamModelSnapshotContainsRequiredIntegrationRoutes() throws IOException {
        JsonNode paths = read(MODEL_CONTRACT).path("paths");
        Set<String> required = Set.of(
                "/health/live",
                "/health/ready",
                "/api/v1/meta",
                "/api/v1/substances/resolve",
                "/api/v1/substances/discover",
                "/api/v1/evidence/search",
                "/api/v1/facilities/candidates",
                "/api/v1/conflicts/review",
                "/api/v1/incidents/analyze",
                "/api/v1/agents/incidents/step"
        );

        assertTrue(fieldNames(paths).containsAll(required));
    }

    @Test
    void incidentAgentContractMakesBackendTheExternalMemoryOwner() throws IOException {
        JsonNode model = read(MODEL_CONTRACT);
        JsonNode operation = model.path("paths")
                .path("/api/v1/agents/incidents/step").path("post");
        assertEquals("#/components/schemas/IncidentAgentStepRequest",
                operation.path("requestBody").path("content").path("application/json")
                        .path("schema").path("$ref").asText());
        assertEquals("#/components/schemas/IncidentAgentStepResponse",
                operation.path("responses").path("200").path("content")
                        .path("application/json").path("schema").path("$ref").asText());

        JsonNode integration = read(INTEGRATION_CONTRACT);
        JsonNode agent = integration.path("service").path("incident_agent_endpoint");
        assertEquals("/api/v1/agents/incidents/step", agent.path("path").asText());
        assertEquals("BE_Repository", agent.path("memory_owner").asText());
        assertFalse(agent.path("server_side_session_storage").asBoolean(true));

        JsonNode loop = integration.path("dashboard_bff").path("incident_agent_loop");
        assertEquals("BE_PERSISTED_EXTERNAL_MEMORY", loop.path("memory_mode").asText());
        assertFalse(loop.path("memory_can_trigger_rule").asBoolean(true));
        assertFalse(loop.path("trace_is_chain_of_thought").asBoolean(true));
        assertTrue(loop.path("backend_compare_and_swap_fields").toString()
                .contains("parent_memory_sha256"));

        JsonNode request = read(Path.of(
                "contracts/examples/model/incident_agent_step_request.json"));
        assertFalse(request.path("analysis").path("incident_id").asText().isBlank());
        assertTrue(request.path("memory").isMissingNode());
        assertEquals(6, request.path("max_actions").asInt());
    }

    @Test
    void fixturesPreserveFailureAndMovementSafetySemantics() throws IOException {
        Path fixtureDirectory = Path.of("contracts/examples/bff");
        List<Path> fixtures;
        try (var files = Files.list(fixtureDirectory)) {
            fixtures = files.filter(path -> path.toString().endsWith(".json")).sorted().toList();
        }

        assertTrue(fixtures.size() >= 16);
        for (Path fixture : fixtures) {
            assertNotNull(read(fixture), fixture.toString());
        }

        assertTrue(read(fixtureDirectory.resolve("record_save_success_response.json"))
                .path("resetAllowed").asBoolean());
        assertFalse(read(fixtureDirectory.resolve("record_save_failure_response.json"))
                .path("resetAllowed").asBoolean(true));
        assertEquals("MODEL_TIMEOUT", read(fixtureDirectory.resolve("model_timeout_error_response.json"))
                .path("error").path("code").asText());
        assertTrue(read(fixtureDirectory.resolve("model_timeout_error_response.json"))
                .path("error").path("retryable").asBoolean());

        JsonNode movementRequest = read(fixtureDirectory.resolve("movement_update_request.json"));
        JsonNode movementResponse = read(fixtureDirectory.resolve("movement_update_response.json"));
        assertEquals(movementRequest.path("clientSequence").asInt(), movementResponse.path("clientSequence").asInt());
        assertFalse(movementResponse.path("mapContext").path("route")
                .path("progressRatioIsProbability").asBoolean(true));
        assertEquals("NOT_COMPUTED_NO_VALIDATED_DISPERSION_MODEL",
                movementResponse.path("mapContext").path("hazardOverlayStatus").asText());
    }

    @Test
    void lockedArtifactHashesMatchRepositoryFiles() throws IOException, NoSuchAlgorithmException {
        JsonNode lock = read(Path.of("contracts/contract-lock.json"));
        assertEquals("e24fa93d538229844af4377976ee5a180c881fb3", lock.path("sourceMergeCommit").asText());
        assertEquals("c4febf23d8ea20ce01a90f2c683ac4667e867d1c",
                lock.path("latestAuditedMainCommit").asText());
        assertTrue(lock.path("upstreamChanges").toString()
                .contains("32736a680d445acea158da42efebd87651ce11b2"));

        for (JsonNode artifact : lock.path("artifacts")) {
            Path path = Path.of(artifact.path("path").asText());
            assertEquals(artifact.path("sha256").asText(), sha256(path), path.toString());
        }
    }

    private static void assertTimeoutResponse(JsonNode contract, String path) {
        JsonNode response = contract.path("paths").path(path).path("post").path("responses").path("504");
        assertFalse(response.isMissingNode(), path);
        assertTrue(response.path("description").asText().contains("MODEL_TIMEOUT"), path);
        assertEquals("#/components/schemas/DashboardErrorResponse",
                response.path("content").path("application/json").path("schema").path("$ref").asText());
    }

    private static void assertSpeechTimeoutResponse(JsonNode contract, String path) {
        JsonNode response = contract.path("paths").path(path).path("post")
                .path("responses").path("504");
        assertFalse(response.isMissingNode(), path);
        assertTrue(response.path("description").asText().contains("SPEECH_TIMEOUT"), path);
        assertEquals("#/components/schemas/DashboardErrorResponse",
                response.path("content").path("application/json").path("schema")
                        .path("$ref").asText());
    }

    private static boolean hasResponse(JsonNode contract, String path, String status) {
        return contract.path("paths").path(path).path("post").path("responses").has(status);
    }

    private static JsonNode read(Path path) throws IOException {
        return OBJECT_MAPPER.readTree(path.toFile());
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new java.util.LinkedHashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static Set<String> jsonTextSet(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.asText()));
        return Set.copyOf(values);
    }

    private static String sha256(Path path) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(path)));
    }
}
