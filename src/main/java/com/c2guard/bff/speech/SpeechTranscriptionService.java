package com.c2guard.bff.speech;

import com.c2guard.integration.speech.SpeechApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

@Service
class SpeechTranscriptionService {

    private final SpeechApiClient speechApiClient;
    private final SpeechTranscriptionProjector projector;

    SpeechTranscriptionService(SpeechApiClient speechApiClient,
                               SpeechTranscriptionProjector projector) {
        this.speechApiClient = speechApiClient;
        this.projector = projector;
    }

    JsonNode transcribe(String incidentId, byte[] audio, MediaType mediaType,
                        String requestId) {
        JsonNode upstream = speechApiClient.transcribe(audio, mediaType, requestId);
        return projector.project(upstream, requestId, incidentId);
    }
}
