package com.c2guard.bff.speech;

import com.c2guard.bff.common.BffContractException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpeechTranscriptionProjectorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SpeechTranscriptionProjector projector = new SpeechTranscriptionProjector();

    @Test
    void projectsOnlyTheBoundedSpeechContract() throws IOException {
        JsonNode projected = projector.project(fixture(), "REQ-SPEECH-0001",
                "INC-SPEECH-0001");

        assertEquals("chemicheck119-dashboard-bff-v1",
                projected.path("schemaVersion").asText());
        assertEquals("REQ-SPEECH-0001", projected.path("requestId").asText());
        assertEquals("INC-SPEECH-0001", projected.path("incidentId").asText());
        assertEquals("아세톤 누출 의심",
                projected.path("transcript").path("text").asText());
        assertTrue(projected.path("requiresResponderReview").asBoolean());
        assertFalse(projected.path("input").path("audioRetained").asBoolean(true));
        assertFalse(projected.path("runtime").path("hotwordsUsed").asBoolean(true));
        assertFalse(projected.path("safetyBoundary")
                .path("casConfirmationPerformed").asBoolean(true));
        assertFalse(projected.path("safetyBoundary")
                .path("riskAssessmentPerformed").asBoolean(true));
        assertTrue(projected.path("safetyBoundary")
                .path("decisionSupportOnly").asBoolean());
        assertFalse(projected.has("schema_version"));
        assertFalse(projected.toString().contains("requested_device"));
    }

    @Test
    void rejectsExtraFieldsAndSafetyBoundaryViolations() throws IOException {
        ObjectNode extra = (ObjectNode) fixture();
        extra.put("cas_number", "67-64-1");
        assertViolation(extra);

        ObjectNode unsafe = (ObjectNode) fixture();
        ((ObjectNode) unsafe.path("safety_boundary"))
                .put("cas_confirmation_performed", true);
        assertViolation(unsafe);

        ObjectNode calibrated = (ObjectNode) fixture();
        ((ObjectNode) calibrated.path("transcript").path("segments").get(0)
                .path("quality_signals"))
                .put("calibrated_correctness_probability", true);
        assertViolation(calibrated);
    }

    @Test
    void rejectsInconsistentAbstentionAndTiming() throws IOException {
        ObjectNode inconsistent = (ObjectNode) fixture();
        inconsistent.put("status", "ABSTAINED_NO_TRANSCRIPT");
        inconsistent.put("abstained", true);
        assertViolation(inconsistent);

        ObjectNode timing = (ObjectNode) fixture();
        ((ObjectNode) timing.path("transcript")).put("audio_seconds", 5.0);
        assertViolation(timing);
    }

    private void assertViolation(JsonNode source) {
        BffContractException error = assertThrows(BffContractException.class,
                () -> projector.project(source, "REQ-SPEECH-0001", "INC-SPEECH-0001"));
        assertEquals(422, error.getStatus());
        assertEquals("SPEECH_CONTRACT_VIOLATION", error.getCode());
        assertFalse(error.isRetryable());
    }

    private JsonNode fixture() throws IOException {
        return objectMapper.readTree(Files.readString(Path.of(
                "src/test/resources/fixtures/speech/transcription_response.json")));
    }
}
