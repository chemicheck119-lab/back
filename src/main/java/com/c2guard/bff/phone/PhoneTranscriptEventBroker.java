package com.c2guard.bff.phone;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class PhoneTranscriptEventBroker {

    private static final int MAX_BUFFERED_EVENTS = 100;
    private static final Duration STREAM_TIMEOUT = Duration.ofMinutes(30);

    private final AtomicLong sequence = new AtomicLong();
    private final Map<String, BoundedHistory> history = new ConcurrentHashMap<>();
    private final Map<String, CopyOnWriteArrayList<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    public PhoneTranscriptEvent publish(String incidentId, String transcriptId, String callId,
                                        String text, String language, boolean isFinal,
                                        String reviewStatus, java.time.OffsetDateTime receivedAt) {
        PhoneTranscriptEvent event = new PhoneTranscriptEvent(sequence.incrementAndGet(),
                incidentId, transcriptId, callId, text, language, isFinal, reviewStatus, receivedAt);
        history.computeIfAbsent(incidentId, ignored -> new BoundedHistory()).synchronizedAdd(event);
        for (SseEmitter emitter : subscribers.getOrDefault(incidentId, new CopyOnWriteArrayList<>())) {
            try {
                emitter.send(SseEmitter.event().id(Long.toString(event.eventId()))
                        .name("phone.transcript")
                        .reconnectTime(5000L)
                        .data(event));
            } catch (IOException error) {
                emitter.completeWithError(error);
                subscribers.getOrDefault(incidentId, new CopyOnWriteArrayList<>()).remove(emitter);
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
        long after = parseEventId(lastEventId);
        BoundedHistory events = history.get(incidentId);
        if (events != null) {
            synchronized (events) {
                events.stream().filter(event -> event.eventId() > after).forEach(event -> send(emitter, event));
            }
        }
        return emitter;
    }

    private void send(SseEmitter emitter, PhoneTranscriptEvent event) {
        try {
            emitter.send(SseEmitter.event().id(Long.toString(event.eventId()))
                    .name("phone.transcript")
                    .reconnectTime(5000L)
                    .data(event));
        } catch (IOException error) {
            emitter.completeWithError(error);
        }
    }

    private long parseEventId(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static final class BoundedHistory extends ArrayDeque<PhoneTranscriptEvent> {
        void synchronizedAdd(PhoneTranscriptEvent event) {
            synchronized (this) {
                addLast(event);
                while (size() > MAX_BUFFERED_EVENTS) {
                    removeFirst();
                }
            }
        }
    }
}
