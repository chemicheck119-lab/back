package com.c2guard.bff.speech;

import com.c2guard.bff.common.BffContractException;
import com.c2guard.integration.speech.SpeechApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.util.concurrent.Semaphore;

@Service
class SpeechTranscriptionService {

    private final SpeechApiClient speechApiClient;
    private final SpeechTranscriptionProjector projector;
    private final Semaphore transcriptionGate = new Semaphore(1, true);

    SpeechTranscriptionService(SpeechApiClient speechApiClient,
                               SpeechTranscriptionProjector projector) {
        this.speechApiClient = speechApiClient;
        this.projector = projector;
    }

    JsonNode transcribe(String incidentId, byte[] audio, MediaType mediaType,
                        String requestId) {
        if (!transcriptionGate.tryAcquire()) {
            throw new BffContractException(429, "SPEECH_BUSY",
                    "음성 전사 요청이 많습니다. 잠시 후 다시 시도하세요.", true);
        }
        try {
            JsonNode upstream = speechApiClient.transcribe(audio, mediaType, requestId);
            return projector.project(upstream, requestId, incidentId);
        } finally {
            transcriptionGate.release();
        }
    }
}
