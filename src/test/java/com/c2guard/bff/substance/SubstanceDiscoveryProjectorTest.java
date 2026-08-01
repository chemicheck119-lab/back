package com.c2guard.bff.substance;

import com.c2guard.bff.common.BffContractException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SubstanceDiscoveryProjectorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SubstanceDiscoveryProjector projector =
            new SubstanceDiscoveryProjector(objectMapper);

    @Test
    void projectsCandidateResultsExactlyToTheClientFixture() throws Exception {
        JsonNode model = load("src/test/resources/fixtures/model/material_discovery_response.json");
        JsonNode expected = load(
                "contracts/examples/bff/material_discovery_candidates_response.json");

        JsonNode actual = projector.project(model, "REQ-BFF-EXAMPLE-0001",
                model.path("query").asText());

        assertEquals(expected, actual);
        assertFalse(actual.path("riskDisplayAllowed").asBoolean());
        assertFalse(actual.path("candidateScoreIsProbability").asBoolean(true));
        assertFalse(actual.toString().contains("method"));
        assertFalse(actual.toString().contains("useDescription"));
    }

    @Test
    void projectsNoMatchAsAbstentionRatherThanAbsenceOrSafety() throws Exception {
        ObjectNode model = (ObjectNode) load(
                "src/test/resources/fixtures/model/material_discovery_response.json");
        JsonNode expected = load("contracts/examples/bff/material_discovery_no_match_response.json");
        model.put("query", expected.path("query").asText());
        model.put("status", "NO_RELIABLE_CANDIDATE");
        model.put("search_mode", "ABSTAINED");
        model.set("candidates", objectMapper.createArrayNode());
        model.set("notice", expected.path("notice"));
        model.set("safety_notice", expected.path("safetyNotice"));

        JsonNode actual = projector.project(model, expected.path("requestId").asText(),
                expected.path("query").asText());

        assertEquals(expected, actual);
        assertFalse(actual.path("noReliableCandidateMeansAbsent").asBoolean(true));
        assertFalse(actual.path("noReliableCandidateMeansSafe").asBoolean(true));
    }

    @Test
    void rejectsACandidateThatClaimsRuleEligibilityBeforeConfirmation() throws Exception {
        ObjectNode model = (ObjectNode) load(
                "src/test/resources/fixtures/model/material_discovery_response.json");
        ((ObjectNode) model.path("candidates").path(0)).put("rule_eligible", true);

        BffContractException error = assertThrows(BffContractException.class,
                () -> projector.project(model, "REQ-BFF-EXAMPLE-0001",
                        model.path("query").asText()));

        assertEquals(422, error.getStatus());
        assertEquals("MODEL_CONTRACT_VIOLATION", error.getCode());
    }

    @Test
    void rejectsEvidenceThatBelongsToAnotherCas() throws Exception {
        ObjectNode model = (ObjectNode) load(
                "src/test/resources/fixtures/model/material_discovery_response.json");
        ObjectNode candidate = (ObjectNode) model.path("candidates").path(0);
        ObjectNode evidence = objectMapper.createObjectNode();
        evidence.put("evidence_id", "EVD-WRONG-CAS");
        evidence.put("cas_number", "7647-01-0");
        evidence.put("source", "KOSHA");
        evidence.put("title", "다른 물질의 근거");
        evidence.put("body_preview", "잘못 연결된 근거");
        evidence.put("source_url", "https://example.invalid/evidence");
        evidence.put("document_version", "test");
        candidate.putArray("evidence").add(evidence);

        assertThrows(BffContractException.class,
                () -> projector.project(model, "REQ-BFF-EXAMPLE-0001",
                        model.path("query").asText()));
    }

    private JsonNode load(String path) throws Exception {
        return objectMapper.readTree(Files.readString(Path.of(path)));
    }
}
