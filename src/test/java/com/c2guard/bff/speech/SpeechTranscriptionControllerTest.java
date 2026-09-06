package com.c2guard.bff.speech;

import com.c2guard.integration.speech.SpeechApiClient;
import com.c2guard.integration.speech.SpeechApiErrorKind;
import com.c2guard.integration.speech.SpeechApiException;
import com.c2guard.security.SignedSessionTokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static com.c2guard.security.BffTestSession.responder;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "chemicheck119.speech-api.max-audio-bytes=64")
@AutoConfigureMockMvc
class SpeechTranscriptionControllerTest {

    private static final String INCIDENT_ID = "INC-SPEECH-0001";
    private static final String REQUEST_ID = "REQ-SPEECH-0001";
    private static final String PATH = "/api/c2guard/v1/incidents/" + INCIDENT_ID
            + "/transcriptions";
    private static final String PRE_INCIDENT_PATH = "/api/c2guard/v1/transcriptions";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SignedSessionTokenService tokenService;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SpeechApiClient speechApiClient;

    @Test
    void authenticatedIncidentScopedRequestReturnsBoundedProjection() throws Exception {
        byte[] audio = wav(44);
        JsonNode expected = objectMapper.readTree(Files.readString(Path.of(
                "contracts/examples/bff/speech_transcription_response.json")));
        when(speechApiClient.transcribe(argThat(value -> Arrays.equals(value, audio)),
                eq(MediaType.parseMediaType("audio/wav")), eq(REQUEST_ID)))
                .thenReturn(fixture());

        mockMvc.perform(post(PATH)
                        .cookie(responder(tokenService, INCIDENT_ID))
                        .header("X-Request-Id", REQUEST_ID)
                        .contentType("audio/wav")
                        .content(audio))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", REQUEST_ID))
                .andExpect(content().json(expected.toString(), true))
                .andExpect(jsonPath("$.schemaVersion")
                        .value("chemicheck119-dashboard-bff-v1"))
                .andExpect(jsonPath("$.incidentId").value(INCIDENT_ID))
                .andExpect(jsonPath("$.transcript.text").value("아세톤 누출 의심"))
                .andExpect(jsonPath("$.requiresResponderReview").value(true))
                .andExpect(jsonPath("$.input.audioRetained").value(false))
                .andExpect(jsonPath("$.safetyBoundary.casConfirmationPerformed")
                        .value(false))
                .andExpect(jsonPath("$.safetyBoundary.riskAssessmentPerformed")
                        .value(false));

        verify(speechApiClient).transcribe(
                argThat(value -> Arrays.equals(value, audio)),
                eq(MediaType.parseMediaType("audio/wav")), eq(REQUEST_ID));
    }

    @Test
    void requiresAuthenticationAndMatchingIncidentScope() throws Exception {
        mockMvc.perform(post(PATH).contentType("audio/wav").content(wav(44)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post(PATH)
                        .cookie(responder(tokenService, "INC-DIFFERENT"))
                        .contentType("audio/wav").content(wav(44)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(speechApiClient);
    }

    @Test
    void authenticatedSessionCanTranscribeBeforeIncidentCreationWithoutInventingScope()
            throws Exception {
        byte[] audio = wav(44);
        when(speechApiClient.transcribe(argThat(value -> Arrays.equals(value, audio)),
                eq(MediaType.parseMediaType("audio/wav")), eq(REQUEST_ID)))
                .thenReturn(fixture());

        mockMvc.perform(post(PRE_INCIDENT_PATH)
                        .cookie(responder(tokenService))
                        .header("X-Request-Id", REQUEST_ID)
                        .contentType("audio/wav").content(audio))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.requiresResponderReview").value(true));
    }

    @Test
    void preIncidentTranscriptionStillRequiresAuthentication() throws Exception {
        mockMvc.perform(post(PRE_INCIDENT_PATH)
                        .contentType("audio/wav").content(wav(44)))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(speechApiClient);
    }

    @Test
    void rejectsOversizedMalformedAndUnsupportedAudioBeforeInference() throws Exception {
        mockMvc.perform(post(PATH)
                        .cookie(responder(tokenService, INCIDENT_ID))
                        .contentType("audio/wav").content(wav(65)))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.error.code").value("AUDIO_SIZE_OUT_OF_RANGE"));

        mockMvc.perform(post(PATH)
                        .cookie(responder(tokenService, INCIDENT_ID))
                        .contentType("audio/wav").content(new byte[44]))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("INVALID_WAV"));

        mockMvc.perform(post(PATH)
                        .cookie(responder(tokenService, INCIDENT_ID))
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(wav(44)))
                .andExpect(status().isUnsupportedMediaType());

        verifyNoInteractions(speechApiClient);
    }

    @Test
    void mapsBusyUpstreamToRetryable429WithoutLeakingDetails() throws Exception {
        when(speechApiClient.transcribe(argThat(value -> value.length == 44),
                eq(MediaType.parseMediaType("audio/wav")), eq(REQUEST_ID)))
                .thenThrow(new SpeechApiException(SpeechApiErrorKind.BUSY,
                        "QUEUE_TIMEOUT", "upstream internal detail", true,
                        429, REQUEST_ID, null));

        mockMvc.perform(post(PATH)
                        .cookie(responder(tokenService, INCIDENT_ID))
                        .header("X-Request-Id", REQUEST_ID)
                        .contentType("audio/wav").content(wav(44)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("SPEECH_BUSY"))
                .andExpect(jsonPath("$.error.retryable").value(true))
                .andExpect(jsonPath("$.error.message")
                        .value("음성 전사 요청이 많습니다. 잠시 후 다시 시도하세요."));
    }

    private JsonNode fixture() throws Exception {
        return objectMapper.readTree(Files.readString(Path.of(
                "src/test/resources/fixtures/speech/transcription_response.json")));
    }

    private byte[] wav(int size) {
        byte[] result = new byte[size];
        if (size >= 12) {
            System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, result, 0, 4);
            System.arraycopy("WAVE".getBytes(StandardCharsets.US_ASCII), 0, result, 8, 4);
        }
        return result;
    }
}
