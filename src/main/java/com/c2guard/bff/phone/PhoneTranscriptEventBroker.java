package com.c2guard.bff.phone;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class PhoneTranscriptEventBroker {

    private static final int MAX_REPLAY_EVENTS = 100;
    // Rotate before the 60-second Cloud Run request timeout; EventSource resumes
    // with Last-Event-ID and the persisted transcript history.
    private static final Duration STREAM_TIMEOUT = Duration.ofSeconds(45);

    private final PhoneTranscriptStore transcriptStore;
    private final Map<String, CopyOnWriteArrayList<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    public PhoneTranscriptEventBroker(PhoneTranscriptStore transcriptStore) {
        this.transcriptStore = transcriptStore;
    }

    public PhoneTranscriptEvent publish(PhoneTranscriptStore.StoredTranscript stored) {
        PhoneTranscriptEvent event = toEvent(stored);
        for (SseEmitter emitter : subscribers.getOrDefault(
                stored.incidentId(), new CopyOnWriteArrayList<>())) {
            send(stored.incidentId(), emitter, transcriptEvent(event));
        }
        return event;
    }

    public SseEmitter open(String incidentId, String lastEventId) {
        SseEmitter emitter = createEmitter();
        CopyOnWriteArrayList<SseEmitter> incidentSubscribers = subscribers
                .computeIfAbsent(incidentId, ignored -> new CopyOnWriteArrayList<>());
        incidentSubscribers.add(emitter);
        Runnable remove = () -> incidentSubscribers.remove(emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(() -> {
            remove.run();
            emitter.complete();
        });
        emitter.onError(ignored -> remove.run());
        // Flush headers even when no transcript exists yet. An idle SSE request
        // must not look like an unresponsive API request to the proxy.
        if (!send(incidentId, emitter, SseEmitter.event().comment("connected").reconnectTime(1000L))) {
            return emitter;
        }
        for (var stored : transcriptStore.findReplayEvents(incidentId, lastEventId, MAX_REPLAY_EVENTS)) {
            if (!send(incidentId, emitter, transcriptEvent(toEvent(stored)))) break;
        }
        return emitter;
    }

    protected SseEmitter createEmitter() {
        return new SseEmitter(STREAM_TIMEOUT.toMillis());
    }

    private SseEmitter.SseEventBuilder transcriptEvent(PhoneTranscriptEvent event) {
        return SseEmitter.event().id(event.eventId()).name("phone.transcript")
                .reconnectTime(1000L).data(event);
    }

    private boolean send(String incidentId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
            return true;
        } catch (IOException | IllegalStateException error) {
            // A disconnected browser must not fail an already persisted
            // provider transcript, nor prevent delivery to other subscribers.
            subscribers.getOrDefault(incidentId, new CopyOnWriteArrayList<>()).remove(emitter);
            try {
                emitter.completeWithError(error);
            } catch (IllegalStateException alreadyCompleted) {
                // Servlet async processing can finish before this cleanup.
            }
            return false;
        }
    }

    private PhoneTranscriptEvent toEvent(PhoneTranscriptStore.StoredTranscript stored) {
        return new PhoneTranscriptEvent(stored.streamEventId(), stored.incidentId(),
                stored.transcriptId(), stored.callId(), stored.text(), stored.language(),
                stored.isFinal(), stored.reviewStatus(), stored.revision(),
                stored.segmentIndex(), stored.reviewedByUserId(), stored.reviewedAt(),
                stored.acceptedAt());
    }
}
