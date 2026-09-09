package com.c2guard.bff.speech;

import com.c2guard.bff.common.BffContractException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

@Component
class SpeechTranscriptionProjector {

    static final String SPEECH_SCHEMA = "chemicheck119-speech-api-v1";
    static final String BFF_SCHEMA = "chemicheck119-dashboard-bff-v1";

    private static final int MAX_TRANSCRIPT_CHARACTERS = 20_000;
    private static final int MAX_SEGMENT_CHARACTERS = 2_000;
    private static final int MAX_SEGMENTS = 2_000;
    private static final Pattern GIT_COMMIT_PATTERN = Pattern.compile("[0-9a-f]{40}");
    private static final Pattern SHA256_PATTERN = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern MODEL_REPOSITORY_PATTERN = Pattern.compile(
            "[A-Za-z0-9][A-Za-z0-9._-]*/[A-Za-z0-9][A-Za-z0-9._-]*");
    private static final Set<String> LEGACY_RUNTIME_FIELDS = Set.of(
            "implementation", "package_version", "service_version", "model",
            "requested_device", "requested_compute_type", "actual_device",
            "actual_compute_type", "initialization_fallback", "hotwords_used",
            "processing_seconds", "real_time_factor");
    private static final Set<String> PROVENANCE_RUNTIME_FIELDS = Set.of(
            "implementation", "package_version", "service_version", "service_git_commit",
            "model", "model_repository", "model_revision", "model_bin_sha256",
            "model_artifact_verified", "requested_device", "requested_compute_type",
            "actual_device", "actual_compute_type", "initialization_fallback",
            "hotwords_used", "processing_seconds", "real_time_factor");

    JsonNode project(JsonNode source, String requestId, String incidentId) {
        requireObject(source, "response");
        requireExactFields(source, Set.of("schema_version", "request_id", "status",
                "abstained", "transcript", "input", "runtime", "safety_boundary"),
                "response");
        requireTextEquals(source, "schema_version", SPEECH_SCHEMA);
        requireTextEquals(source, "request_id", requestId);

        String status = requireText(source, "status", 64);
        boolean abstained = requireBoolean(source, "abstained");
        JsonNode transcript = requireObject(source.path("transcript"), "transcript");
        requireExactFields(transcript,
                Set.of("text", "segments", "audio_seconds", "voiced_seconds"),
                "transcript");
        String text = requireTextAllowEmpty(transcript, "text", MAX_TRANSCRIPT_CHARACTERS);
        JsonNode segments = transcript.path("segments");
        if (!segments.isArray() || segments.size() > MAX_SEGMENTS) {
            throw violation("Speech transcript segments가 올바르지 않습니다.");
        }
        if ("TRANSCRIBED".equals(status)) {
            if (abstained || text.isBlank()) {
                throw violation("Speech 전사 상태와 기권 여부가 일치하지 않습니다.");
            }
        } else if ("ABSTAINED_NO_TRANSCRIPT".equals(status)) {
            if (!abstained || !text.isBlank() || !segments.isEmpty()) {
                throw violation("Speech 기권 상태와 전사문이 일치하지 않습니다.");
            }
        } else {
            throw violation("지원하지 않는 Speech 전사 상태입니다.");
        }

        double audioSeconds = requirePositiveNumber(transcript, "audio_seconds");
        double voicedSeconds = requireNonNegativeNumber(transcript, "voiced_seconds");
        if (voicedSeconds > audioSeconds + 0.1) {
            throw violation("Speech voiced seconds가 audio seconds를 초과합니다.");
        }

        ArrayNode projectedSegments = JsonNodeFactory.instance.arrayNode();
        double previousEnd = 0.0;
        for (JsonNode segment : segments) {
            requireObject(segment, "segment");
            requireExactFields(segment,
                    Set.of("start_seconds", "end_seconds", "text", "quality_signals"),
                    "segment");
            double start = requireNonNegativeNumber(segment, "start_seconds");
            double end = requireNonNegativeNumber(segment, "end_seconds");
            if (end < start || start + 0.01 < previousEnd || end > audioSeconds + 0.1) {
                throw violation("Speech segment 시간이 올바르지 않습니다.");
            }
            previousEnd = end;
            String segmentText = requireTextAllowEmpty(segment, "text",
                    MAX_SEGMENT_CHARACTERS);
            JsonNode quality = requireObject(segment.path("quality_signals"),
                    "quality_signals");
            requireExactFields(quality,
                    Set.of("avg_log_probability", "no_speech_probability",
                            "compression_ratio", "calibrated_correctness_probability"),
                    "quality_signals");
            double avgLogProbability = requireFiniteNumber(quality,
                    "avg_log_probability");
            double noSpeechProbability = requireFiniteNumber(quality,
                    "no_speech_probability");
            double compressionRatio = requireNonNegativeNumber(quality,
                    "compression_ratio");
            if (noSpeechProbability < 0 || noSpeechProbability > 1
                    || requireBoolean(quality,
                    "calibrated_correctness_probability")) {
                throw violation("Speech 품질 신호의 의미 경계가 올바르지 않습니다.");
            }
            ObjectNode projected = projectedSegments.addObject();
            projected.put("startSeconds", start);
            projected.put("endSeconds", end);
            projected.put("text", segmentText);
            ObjectNode projectedQuality = projected.putObject("qualitySignals");
            projectedQuality.put("avgLogProbability", avgLogProbability);
            projectedQuality.put("noSpeechProbability", noSpeechProbability);
            projectedQuality.put("compressionRatio", compressionRatio);
            projectedQuality.put("calibratedCorrectnessProbability", false);
        }

        JsonNode input = requireObject(source.path("input"), "input");
        requireExactFields(input, Set.of("media_type", "channels", "sample_width_bits",
                "sample_rate_hz", "duration_seconds", "audio_retained"), "input");
        requireTextEquals(input, "media_type", "audio/wav");
        int channels = requireInt(input, "channels", 1, 2);
        int sampleWidthBits = requireInt(input, "sample_width_bits", 16, 16);
        int sampleRateHz = requireInt(input, "sample_rate_hz", 8_000, 48_000);
        double durationSeconds = requirePositiveNumber(input, "duration_seconds");
        if (Math.abs(durationSeconds - audioSeconds) > 0.25) {
            throw violation("Speech 입력과 전사 음성 길이가 일치하지 않습니다.");
        }
        if (requireBoolean(input, "audio_retained")) {
            throw violation("Speech API가 원본 음성을 보관한다고 응답했습니다.");
        }

        JsonNode runtime = requireObject(source.path("runtime"), "runtime");
        Set<String> runtimeFields = fieldNames(runtime);
        if (!runtimeFields.equals(LEGACY_RUNTIME_FIELDS)
                && !runtimeFields.equals(PROVENANCE_RUNTIME_FIELDS)) {
            throw violation("Speech runtime 필드 계약이 일치하지 않습니다.");
        }
        requireTextEquals(runtime, "implementation", "faster-whisper");
        requireTextEquals(runtime, "package_version", "1.2.1");
        if (requireBoolean(runtime, "hotwords_used")) {
            throw violation("기각된 hotword 기본값이 Speech API에 사용됐습니다.");
        }
        String serviceGitCommit = null;
        String modelRepository = null;
        String modelRevision = null;
        String modelBinSha256 = null;
        boolean modelArtifactVerified = false;
        if (runtimeFields.equals(PROVENANCE_RUNTIME_FIELDS)) {
            serviceGitCommit = requireNullableMatchingText(runtime, "service_git_commit",
                    40, GIT_COMMIT_PATTERN);
            modelRepository = requireNullableMatchingText(runtime, "model_repository",
                    160, MODEL_REPOSITORY_PATTERN);
            modelRevision = requireNullableMatchingText(runtime, "model_revision",
                    40, GIT_COMMIT_PATTERN);
            modelBinSha256 = requireNullableMatchingText(runtime, "model_bin_sha256",
                    64, SHA256_PATTERN);
            modelArtifactVerified = requireBoolean(runtime, "model_artifact_verified");
            boolean anyModelProvenance = modelRepository != null
                    || modelRevision != null || modelBinSha256 != null;
            boolean completeModelProvenance = modelRepository != null
                    && modelRevision != null && modelBinSha256 != null;
            if (anyModelProvenance != completeModelProvenance
                    || modelArtifactVerified != completeModelProvenance) {
                throw violation("Speech 모델 artifact 검증 상태와 출처 정보가 일치하지 않습니다.");
            }
        }

        JsonNode safety = requireObject(source.path("safety_boundary"), "safety_boundary");
        requireExactFields(safety, Set.of("uncertainty_preserved",
                "quality_signals_are_calibrated_probabilities",
                "chemical_identification_performed", "cas_confirmation_performed",
                "risk_assessment_performed", "decision_support_only"), "safety_boundary");
        if (!requireBoolean(safety, "uncertainty_preserved")
                || requireBoolean(safety, "quality_signals_are_calibrated_probabilities")
                || requireBoolean(safety, "chemical_identification_performed")
                || requireBoolean(safety, "cas_confirmation_performed")
                || requireBoolean(safety, "risk_assessment_performed")
                || !requireBoolean(safety, "decision_support_only")) {
            throw violation("Speech API 안전 책임 경계가 올바르지 않습니다.");
        }

        ObjectNode target = JsonNodeFactory.instance.objectNode();
        target.put("schemaVersion", BFF_SCHEMA);
        target.put("requestId", requestId);
        if (incidentId == null) {
            target.putNull("incidentId");
        } else {
            target.put("incidentId", incidentId);
        }
        target.put("status", status);
        target.put("abstained", abstained);
        target.put("requiresResponderReview", true);
        ObjectNode targetTranscript = target.putObject("transcript");
        targetTranscript.put("text", text);
        targetTranscript.set("segments", projectedSegments);
        targetTranscript.put("audioSeconds", audioSeconds);
        targetTranscript.put("voicedSeconds", voicedSeconds);
        ObjectNode targetInput = target.putObject("input");
        targetInput.put("mediaType", "audio/wav");
        targetInput.put("channels", channels);
        targetInput.put("sampleWidthBits", sampleWidthBits);
        targetInput.put("sampleRateHz", sampleRateHz);
        targetInput.put("durationSeconds", durationSeconds);
        targetInput.put("audioRetained", false);
        ObjectNode targetRuntime = target.putObject("runtime");
        targetRuntime.put("serviceVersion", requireText(runtime, "service_version", 64));
        putNullable(targetRuntime, "serviceGitCommit", serviceGitCommit);
        targetRuntime.put("model", requireText(runtime, "model", 160));
        putNullable(targetRuntime, "modelRepository", modelRepository);
        putNullable(targetRuntime, "modelRevision", modelRevision);
        putNullable(targetRuntime, "modelBinSha256", modelBinSha256);
        targetRuntime.put("modelArtifactVerified", modelArtifactVerified);
        targetRuntime.put("actualDevice", requireText(runtime, "actual_device", 32));
        targetRuntime.put("actualComputeType",
                requireText(runtime, "actual_compute_type", 32));
        targetRuntime.put("processingSeconds",
                requireNonNegativeNumber(runtime, "processing_seconds"));
        targetRuntime.put("realTimeFactor",
                requireNonNegativeNumber(runtime, "real_time_factor"));
        targetRuntime.put("hotwordsUsed", false);
        ObjectNode targetSafety = target.putObject("safetyBoundary");
        targetSafety.put("uncertaintyPreserved", true);
        targetSafety.put("qualitySignalsAreCalibratedProbabilities", false);
        targetSafety.put("chemicalIdentificationPerformed", false);
        targetSafety.put("casConfirmationPerformed", false);
        targetSafety.put("riskAssessmentPerformed", false);
        targetSafety.put("decisionSupportOnly", true);
        return target;
    }

    private void requireExactFields(JsonNode node, Set<String> expected, String label) {
        Set<String> actual = fieldNames(node);
        if (!actual.equals(expected)) {
            throw violation("Speech " + label + " 필드 계약이 일치하지 않습니다.");
        }
    }

    private Set<String> fieldNames(JsonNode node) {
        Set<String> fields = new HashSet<>();
        node.fieldNames().forEachRemaining(fields::add);
        return fields;
    }

    private String requireNullableMatchingText(JsonNode node, String field, int maxLength,
                                               Pattern pattern) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual() || value.asText().isBlank()
                || value.asText().length() > maxLength
                || !pattern.matcher(value.asText()).matches()) {
            throw violation("Speech " + field + " 문자열이 올바르지 않습니다.");
        }
        return value.asText();
    }

    private void putNullable(ObjectNode target, String field, String value) {
        if (value == null) {
            target.putNull(field);
        } else {
            target.put(field, value);
        }
    }

    private JsonNode requireObject(JsonNode node, String label) {
        if (node == null || !node.isObject()) {
            throw violation("Speech " + label + " 객체가 필요합니다.");
        }
        return node;
    }

    private String requireText(JsonNode node, String field, int maxLength) {
        String value = requireTextAllowEmpty(node, field, maxLength);
        if (value.isBlank()) {
            throw violation("Speech " + field + " 값이 비어 있습니다.");
        }
        return value;
    }

    private String requireTextAllowEmpty(JsonNode node, String field, int maxLength) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().length() > maxLength) {
            throw violation("Speech " + field + " 문자열이 올바르지 않습니다.");
        }
        return value.asText();
    }

    private void requireTextEquals(JsonNode node, String field, String expected) {
        if (!expected.equals(node.path(field).asText(null))) {
            throw violation("Speech " + field + " 값이 계약과 일치하지 않습니다.");
        }
    }

    private boolean requireBoolean(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isBoolean()) {
            throw violation("Speech " + field + " boolean이 필요합니다.");
        }
        return value.asBoolean();
    }

    private int requireInt(JsonNode node, String field, int minimum, int maximum) {
        JsonNode value = node.path(field);
        if (!value.isIntegralNumber() || value.asLong() < minimum || value.asLong() > maximum) {
            throw violation("Speech " + field + " 정수가 허용 범위를 벗어났습니다.");
        }
        return value.asInt();
    }

    private double requirePositiveNumber(JsonNode node, String field) {
        double value = requireFiniteNumber(node, field);
        if (value <= 0) {
            throw violation("Speech " + field + " 값은 양수여야 합니다.");
        }
        return value;
    }

    private double requireNonNegativeNumber(JsonNode node, String field) {
        double value = requireFiniteNumber(node, field);
        if (value < 0) {
            throw violation("Speech " + field + " 값은 0 이상이어야 합니다.");
        }
        return value;
    }

    private double requireFiniteNumber(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isNumber() || !Double.isFinite(value.asDouble())) {
            throw violation("Speech " + field + " 숫자가 올바르지 않습니다.");
        }
        return value.asDouble();
    }

    private BffContractException violation(String message) {
        return new BffContractException(422, "SPEECH_CONTRACT_VIOLATION", message, false);
    }
}
