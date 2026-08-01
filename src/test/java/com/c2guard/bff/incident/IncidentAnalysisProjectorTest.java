package com.c2guard.bff.incident;

import com.c2guard.bff.common.BffContractException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IncidentAnalysisProjectorTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final IncidentAnalysisProjector projector = new IncidentAnalysisProjector(objectMapper);

    @Test
    void projectsTheModelAwaitingResponseExactlyToTheClientFixture() throws Exception {
        JsonNode model = load("src/test/resources/fixtures/model/incident_unconfirmed_response.json");
        JsonNode expected = load("contracts/examples/bff/incident_awaiting_confirmation_response.json");

        JsonNode actual = projector.project(model, "REQ-EXAMPLE-0001", "INC-EXAMPLE-0001");

        assertEquals(expected, actual);
        assertFalse(actual.toString().contains("inputFingerprint"));
        assertFalse(actual.toString().contains("latencyMs"));
        assertFalse(actual.toString().contains("riskLevel"));
    }

    @Test
    void projectsCompletedRuleSafetyFieldsWithoutRewritingThem() throws Exception {
        ObjectNode model = (ObjectNode) load(
                "src/test/resources/fixtures/model/incident_unconfirmed_response.json");
        JsonNode expected = load("contracts/examples/bff/incident_screening_completed_response.json");
        JsonNode completedResult = load(
                "src/test/resources/fixtures/model/conflict_screening_completed_result.json");

        model.put("analysis_id", expected.path("analysisId").asText());
        model.put("request_id", expected.path("requestId").asText());
        model.put("incident_id", expected.path("incidentId").asText());
        model.put("state", "SCREENING_COMPLETED");
        ObjectNode outputs = (ObjectNode) model.path("model_outputs");
        outputs.set("parser", toSnakeCase(expected.path("parser")));
        outputs.set("substance_candidates", objectMapper.createArrayNode());
        model.set("grounded_rag", toSnakeCase(expected.path("groundedRag")));
        model.set("confirmation_gate", toSnakeCase(expected.path("confirmationGate")));
        ObjectNode conflict = objectMapper.createObjectNode();
        conflict.put("executed", true);
        conflict.put("status", "SCREENING_COMPLETED");
        conflict.put("gate", "BOTH_CAS_RESPONDER_CONFIRMED");
        conflict.put("policy_mode", "PUBLIC_SOURCE_PILOT_V1");
        conflict.set("result", completedResult);
        model.set("conflict_review", conflict);
        model.set("required_next_steps", expected.path("requiredNextSteps"));
        model.set("provenance", toSnakeCase(expected.path("provenance")));
        model.set("safety_notice", expected.path("safetyNotice"));

        JsonNode actual = projector.project(model, expected.path("requestId").asText(),
                expected.path("incidentId").asText());

        assertEquals(expected, actual);
        assertEquals(completedResult.path("risk_level"),
                actual.path("conflictReview").path("result").path("riskLevel"));
        assertEquals(completedResult.path("reference_assurance").path("registry_sha256"),
                actual.path("conflictReview").path("result")
                        .path("referenceAssurance").path("registrySha256"));
    }

    @Test
    void projectsAnUnqueriedFacilityHistoryWhenTheModelReturnsNull() throws Exception {
        ObjectNode model = (ObjectNode) load(
                "src/test/resources/fixtures/model/incident_unconfirmed_response.json");
        ((ObjectNode) model.path("model_outputs"))
                .putNull("facility_history_candidates");

        JsonNode actual = projector.project(model, "REQ-EXAMPLE-0001",
                "INC-EXAMPLE-0001");

        assertEquals("NOT_QUERIED", actual.path("facilityHistory").path("status").asText());
        assertEquals(0, actual.path("facilityHistory").path("candidates").size());
        assertFalse(actual.path("facilityHistory").path("warning").asText().isBlank());
    }

    @Test
    void projectsCurrentModelEvidenceGroupsAndProvenance() throws Exception {
        ObjectNode model = (ObjectNode) load(
                "src/test/resources/fixtures/model/incident_unconfirmed_response.json");
        ArrayNode evidence = objectMapper.createArrayNode();
        ObjectNode retrieval = evidence.addObject().putObject("retrieval");
        ObjectNode card = retrieval.putArray("results").addObject();
        card.put("evidence_id", "KOSHA:LIVE-001");
        card.put("cas_number", "7782-50-5");
        card.put("source", "KOSHA");
        card.put("title", "염소 MSDS");
        card.put("body_preview", "누출 대응 공식 문서 발췌");
        card.put("source_url", "https://example.com/kosha/live-001");
        card.put("document_version", "2026-08-01");
        model.set("evidence", evidence);

        ObjectNode provenance = (ObjectNode) model.path("provenance");
        provenance.remove("model_version");
        provenance.remove("data_version");
        provenance.remove("final_decision_authority");
        provenance.put("chemiguard119_version", "0.4.0");
        provenance.put("resolver_schema_version", "resolver-v3");
        provenance.put("retriever_schema_version", "retriever-v2");
        provenance.put("decision_support_only", true);

        JsonNode actual = projector.project(model, "REQ-EXAMPLE-0001",
                "INC-EXAMPLE-0001");

        assertEquals("KOSHA:LIVE-001",
                actual.path("evidenceCards").path(0).path("evidenceId").asText());
        assertEquals("0.4.0", actual.path("provenance").path("modelVersion").asText());
        assertEquals("resolver-v3 / retriever-v2",
                actual.path("provenance").path("dataVersion").asText());
        assertEquals("현장 지휘관",
                actual.path("provenance").path("finalDecisionAuthority").asText());
    }

    @Test
    void rejectsAResponseWithADifferentRequestId() throws Exception {
        JsonNode model = load("src/test/resources/fixtures/model/incident_unconfirmed_response.json");

        BffContractException error = assertThrows(BffContractException.class,
                () -> projector.project(model, "REQ-DIFFERENT", "INC-EXAMPLE-0001"));

        assertEquals(422, error.getStatus());
        assertEquals("MODEL_CONTRACT_VIOLATION", error.getCode());
    }

    @Test
    void rejectsRiskExecutionWhileTheConfirmationGateIsClosed() throws Exception {
        ObjectNode model = (ObjectNode) load(
                "src/test/resources/fixtures/model/incident_unconfirmed_response.json");
        model.put("state", "SCREENING_COMPLETED");
        ObjectNode conflict = (ObjectNode) model.path("conflict_review");
        conflict.put("executed", true);
        conflict.put("status", "SCREENING_COMPLETED");
        conflict.set("result", load(
                "src/test/resources/fixtures/model/conflict_screening_completed_result.json"));

        assertThrows(BffContractException.class,
                () -> projector.project(model, "REQ-EXAMPLE-0001", "INC-EXAMPLE-0001"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"VERIFY_REQUIRED", "UNCLASSIFIED", "CAMEO_GROUP_SCREENING_ONLY"})
    void keepsInconclusiveStatesFreeOfRiskOutput(String state) throws Exception {
        ObjectNode model = (ObjectNode) load(
                "src/test/resources/fixtures/model/incident_unconfirmed_response.json");
        model.put("state", state);
        ObjectNode gate = (ObjectNode) model.path("confirmation_gate");
        gate.put("incident_confirmed", true);
        gate.put("facility_confirmed", true);
        gate.put("all_required_confirmed", true);
        gate.put("rule_execution_allowed", true);
        ObjectNode rag = (ObjectNode) model.path("grounded_rag");
        rag.put("status", "NOT_RUN_RULE_NOT_COMPLETED");
        ObjectNode conflict = objectMapper.createObjectNode();
        conflict.put("executed", true);
        conflict.put("status", state);
        conflict.put("gate", "BOTH_CAS_RESPONDER_CONFIRMED");
        conflict.put("policy_mode", "PUBLIC_SOURCE_PILOT_V1");
        ObjectNode result = conflict.putObject("result");
        result.put("status", state);
        result.put("reason", "공개 근거가 충분하지 않습니다.");
        result.put("human_confirmation_required", true);
        model.set("conflict_review", conflict);

        JsonNode actual = projector.project(model, "REQ-EXAMPLE-0001", "INC-EXAMPLE-0001");

        assertFalse(actual.path("riskDisplayAllowed").asBoolean());
        assertFalse(actual.path("conflictReview").path("riskDisplayAllowed").asBoolean());
        assertEquals("INCONCLUSIVE_RESULT",
                actual.path("conflictReview").path("result").path("kind").asText());
        assertFalse(actual.toString().contains("riskLevel"));
    }

    private JsonNode load(String path) throws Exception {
        return objectMapper.readTree(Files.readString(Path.of(path)));
    }

    private JsonNode toSnakeCase(JsonNode source) {
        if (source.isArray()) {
            ArrayNode result = objectMapper.createArrayNode();
            source.forEach(value -> result.add(toSnakeCase(value)));
            return result;
        }
        if (!source.isObject()) {
            return source.deepCopy();
        }
        ObjectNode result = objectMapper.createObjectNode();
        source.fields().forEachRemaining(entry ->
                result.set(camelToSnake(entry.getKey()), toSnakeCase(entry.getValue())));
        return result;
    }

    private String camelToSnake(String value) {
        StringBuilder result = new StringBuilder();
        value.chars().forEach(character -> {
            if (Character.isUpperCase(character)) {
                result.append('_').append((char) Character.toLowerCase(character));
            } else {
                result.append((char) character);
            }
        });
        return result.toString();
    }
}
