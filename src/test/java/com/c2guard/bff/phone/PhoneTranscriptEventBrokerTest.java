package com.c2guard.bff.phone;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PhoneTranscriptEventBrokerTest {

    private final PhoneTranscriptStore store = mock(PhoneTranscriptStore.class);

    @Test
    void rotatesBeforeCloudRunTimeoutAndOpensAnIdleStreamImmediately() throws Exception {
        var broker = new PhoneTranscriptEventBroker(store);
        assertThat(broker.open("INC-1", null).getTimeout()).isEqualTo(45_000L);
        SseEmitter emitter = mock(SseEmitter.class);
        withEmitters(emitter).open("INC-1", null);
        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void disconnectedSubscriberDoesNotFailIngressOrOtherSubscribers() throws Exception {
        SseEmitter disconnected = mock(SseEmitter.class);
        SseEmitter healthy = mock(SseEmitter.class);
        var broker = withEmitters(disconnected, healthy);
        broker.open("INC-1", null);
        broker.open("INC-1", null);
        clearInvocations(disconnected, healthy);
        doThrow(new IllegalStateException("Async context completed"))
                .when(disconnected).send(any(SseEmitter.SseEventBuilder.class));
        doThrow(new IllegalStateException("Already complete"))
                .when(disconnected).completeWithError(any());

        assertThatCode(() -> broker.publish(stored())).doesNotThrowAnyException();
        assertThatCode(() -> broker.publish(stored())).doesNotThrowAnyException();
        verify(disconnected, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        verify(healthy, times(2)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void ioFailureDuringReplayRemovesSubscriberAndStopsReplay() throws Exception {
        SseEmitter disconnected = mock(SseEmitter.class);
        when(store.findReplayEvents("INC-1", "TRX-previous:r0", 100))
                .thenReturn(List.of(stored(), stored()));
        doNothing().doThrow(new IOException("Disconnected"))
                .when(disconnected).send(any(SseEmitter.SseEventBuilder.class));
        var broker = withEmitters(disconnected);
        assertThatCode(() -> broker.open("INC-1", "TRX-previous:r0")).doesNotThrowAnyException();
        broker.publish(stored());
        verify(disconnected, times(2)).send(any(SseEmitter.SseEventBuilder.class));
        verify(disconnected).completeWithError(any(IOException.class));
    }

    private PhoneTranscriptEventBroker withEmitters(SseEmitter... emitters) {
        var queue = new ArrayDeque<>(List.of(emitters));
        return new PhoneTranscriptEventBroker(store) {
            @Override protected SseEmitter createEmitter() { return queue.remove(); }
        };
    }

    private PhoneTranscriptStore.StoredTranscript stored() {
        return new PhoneTranscriptStore.StoredTranscript("TRX-1", "INC-1", "clawops",
                "CALL-1", "EVENT-1", "합성 테스트 전사", "ko", true,
                "FINAL_PENDING_REVIEW", OffsetDateTime.parse("2026-09-21T03:00:00Z"), "REQ-1");
    }
}
