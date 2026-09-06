package com.c2guard.bff.speech;

import com.c2guard.bff.common.BffContractException;
import com.c2guard.bff.common.BffRequestIdFilter;
import com.c2guard.integration.speech.SpeechApiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Validated
@RestController
@RequestMapping("/api/c2guard/v1")
public class SpeechTranscriptionController {

    private final SpeechTranscriptionService service;
    private final SpeechApiProperties properties;

    public SpeechTranscriptionController(SpeechTranscriptionService service,
                                         SpeechApiProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @PostMapping(value = "/transcriptions",
            consumes = {"audio/wav", "audio/x-wav", "audio/wave"},
            produces = MediaType.APPLICATION_JSON_VALUE)
    public JsonNode transcribeBeforeIncident(HttpServletRequest request) {
        return transcribe(null, request);
    }

    @PostMapping(value = "/incidents/{incidentId}/transcriptions",
            consumes = {"audio/wav", "audio/x-wav", "audio/wave"},
            produces = MediaType.APPLICATION_JSON_VALUE)
    public JsonNode transcribeForIncident(
            @PathVariable @Size(min = 1, max = 128) String incidentId,
            HttpServletRequest request) {
        return transcribe(incidentId, request);
    }

    private JsonNode transcribe(String incidentId, HttpServletRequest request) {
        byte[] audio = readBounded(request, properties.getMaxAudioBytes());
        if (!hasRiffWaveHeader(audio)) {
            throw new BffContractException(422, "INVALID_WAV",
                    "유효한 PCM WAV 파일이 아닙니다.", false);
        }
        MediaType mediaType = MediaType.parseMediaType(request.getContentType());
        return service.transcribe(incidentId, audio, mediaType,
                BffRequestIdFilter.current(request));
    }

    private byte[] readBounded(HttpServletRequest request, int maximumBytes) {
        long declared = request.getContentLengthLong();
        if (declared == 0 || declared > maximumBytes) {
            throw new BffContractException(413, "AUDIO_SIZE_OUT_OF_RANGE",
                    "음성 크기가 허용 범위를 벗어났습니다.", false);
        }
        try (InputStream input = request.getInputStream()) {
            byte[] audio = input.readNBytes(maximumBytes + 1);
            if (audio.length == 0 || audio.length > maximumBytes) {
                throw new BffContractException(413, "AUDIO_SIZE_OUT_OF_RANGE",
                        "음성 크기가 허용 범위를 벗어났습니다.", false);
            }
            return audio;
        } catch (IOException error) {
            throw new BffContractException(400, "AUDIO_READ_FAILED",
                    "음성 요청을 읽을 수 없습니다.", true);
        }
    }

    private boolean hasRiffWaveHeader(byte[] audio) {
        if (audio.length < 12) {
            return false;
        }
        String riff = new String(audio, 0, 4, StandardCharsets.US_ASCII);
        String wave = new String(audio, 8, 4, StandardCharsets.US_ASCII);
        return "RIFF".equals(riff) && "WAVE".equals(wave);
    }
}
