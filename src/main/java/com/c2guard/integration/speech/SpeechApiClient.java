package com.c2guard.integration.speech;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;

public interface SpeechApiClient {

    JsonNode transcribe(byte[] audio, MediaType mediaType, String requestId);
}
