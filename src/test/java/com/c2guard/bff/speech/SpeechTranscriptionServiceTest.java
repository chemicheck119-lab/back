package com.c2guard.bff.speech;

import com.c2guard.bff.common.BffContractException;
import com.c2guard.integration.speech.SpeechApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpeechTranscriptionServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void rejectsConcurrentTranscriptionBeforeCallingUpstream() throws Exception {
        SpeechApiClient client = mock(SpeechApiClient.class);
        SpeechTranscriptionProjector projector = new SpeechTranscriptionProjector();
        SpeechTranscriptionService service = new SpeechTranscriptionService(client, projector);
        CountDownLatch upstreamEntered = new CountDownLatch(1);
        CountDownLatch releaseUpstream = new CountDownLatch(1);
        JsonNode fixture = fixture();
        when(client.transcribe(any(byte[].class), any(MediaType.class), anyString()))
                .thenAnswer(ignored -> {
                    upstreamEntered.countDown();
                    if (!releaseUpstream.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("test upstream release timeout");
                    }
                    return fixture;
                });

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<JsonNode> first = executor.submit(() -> service.transcribe(
                    "INC-1", wav(), MediaType.parseMediaType("audio/wav"),
                    "REQ-SPEECH-0001"));
            assertTrue(upstreamEntered.await(2, TimeUnit.SECONDS));

            BffContractException busy = assertThrows(BffContractException.class,
                    () -> service.transcribe("INC-2", wav(),
                            MediaType.parseMediaType("audio/wav"), "REQ-2"));

            assertEquals(429, busy.getStatus());
            assertEquals("SPEECH_BUSY", busy.getCode());
            assertTrue(busy.isRetryable());
            releaseUpstream.countDown();
            assertEquals("REQ-SPEECH-0001", first.get(2, TimeUnit.SECONDS)
                    .path("requestId").asText());
            verify(client, times(1)).transcribe(any(byte[].class),
                    any(MediaType.class), anyString());
        } finally {
            releaseUpstream.countDown();
            executor.shutdownNow();
        }
    }

    private JsonNode fixture() throws Exception {
        return objectMapper.readTree(Files.readString(Path.of(
                "src/test/resources/fixtures/speech/transcription_response.json")));
    }

    private byte[] wav() {
        byte[] result = new byte[44];
        System.arraycopy("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                0, result, 0, 4);
        System.arraycopy("WAVE".getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                0, result, 8, 4);
        return result;
    }
}
