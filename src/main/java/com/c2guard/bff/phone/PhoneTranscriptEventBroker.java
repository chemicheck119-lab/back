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
    private static final Duration STREAM_TIMEOUT = Duration.ofMinutes(30);

    private final PhoneTranscriptStore transcriptStore;
    private final Map<String, CopyOnWriteArrayList<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    public PhoneTranscriptEventBroker(PhoneTranscriptStore transcriptStore) {
        this.transcriptStore = transcriptStore;
    }

    public PhoneTranscriptEvent publish(PhoneTranscriptStore.StoredTranscript stored) {
        PhoneTranscriptEvent event = toEvent(stored);
        for (SseEmitter emitter : subscribers.getOrDefault(
                stored.incidentId(), new CopyOnWriteArrayList<>())) {
            try {
                emitter.send(SseEmitter.event().id(event.eventId())
                        .name("phone.transcript")
                        .reconnectTime(5000L)
                        .data(event));
            } catch (IOException error) {
                emitter.completeWithError(error);
                subscribers.getOrDefault(stored.incidentId(),
                        new CopyOnWriteArrayList<>()).remove(emitter);
            }
        }
        return event;
    }

    public SseEmitter open(String incidentId, String lastEventId) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT.toMillis());
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
        transcriptStore.findReplayEvents(incidentId, lastEventId, MAX_REPLAY_EVENTS)
                .stream().map(this::toEvent).forEach(event -> send(emitter, event));
        return emitter;
    }

    private void send(SseEmitter emitter, PhoneTranscriptEvent event) {
        try {
            emitter.send(SseEmitter.event().id(event.eventId())
                    .name("phone.transcript")
                    .reconnectTime(5000L)
                    .data(event));
        } catch (IOException error) {
            emitter.completeWithError(error);
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
