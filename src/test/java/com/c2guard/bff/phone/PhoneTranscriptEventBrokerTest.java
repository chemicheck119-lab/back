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

    @Test
    void limitsAreAtomicAndCallbacksReleaseOnlyOnce() throws Exception {
        var limits = new PhoneStreamProperties();
        limits.setMaxConnections(2);
        limits.setMaxPerIncident(1);
        var broker = new PhoneTranscriptEventBroker(store, limits);
        var first = broker.open("A", null);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> broker.open("A", null))
                .isInstanceOf(PhoneStreamCapacityException.class);
        broker.open("B", null);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> broker.open("C", null))
                .isInstanceOf(PhoneStreamCapacityException.class);
        assertThat(broker.activeConnectionCount()).isEqualTo(2);
        // Real MVC lifecycle callbacks are verified in the HTTP suite. For this
        // unit test, capture callbacks to exercise timeout/error/completion order.
        var emitter = mock(SseEmitter.class);
        var callbacks = new java.util.ArrayList<Runnable>();
        doAnswer(call -> { callbacks.add(call.getArgument(0)); return null; }).when(emitter).onCompletion(any());
        doAnswer(call -> { callbacks.add(call.getArgument(0)); return null; }).when(emitter).onTimeout(any());
        var isolated = new PhoneTranscriptEventBroker(store, limits) {
            @Override protected SseEmitter createEmitter() { return emitter; }
        };
        isolated.open("A", null);
        callbacks.forEach(Runnable::run);
        assertThat(isolated.activeConnectionCount()).isZero();
        assertThat(isolated.activeIncidentCount()).isZero();
        isolated.open("A", null);
        assertThat(isolated.activeConnectionCount()).isEqualTo(1);
    }

    @Test
    void concurrentConnectionsCannotExceedGlobalLimit() throws Exception {
        var limits = new PhoneStreamProperties();
        limits.setMaxConnections(3);
        var broker = new PhoneTranscriptEventBroker(store, limits);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(12);
        try {
            var jobs = new java.util.ArrayList<java.util.concurrent.Callable<Boolean>>();
            for (int n = 0; n < 24; n++) {
                final int id = n;
                jobs.add(() -> { try { broker.open("A-" + id, null); return true; }
                    catch (PhoneStreamCapacityException full) { return false; } });
            }
            int accepted = 0;
            for (var job : pool.invokeAll(jobs)) if (job.get()) accepted++;
            assertThat(accepted).isEqualTo(3);
            assertThat(broker.activeConnectionCount()).isEqualTo(3);
        } finally { pool.shutdownNow(); }
    }

    @Test
    void replayDatabaseFailureReleasesSlotAndEmptyIncidentEntry() {
        when(store.findReplayEvents("FAIL", null, 100)).thenThrow(new IllegalStateException("database unavailable"));
        var broker = new PhoneTranscriptEventBroker(store);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> broker.open("FAIL", null))
                .isInstanceOf(IllegalStateException.class);
        assertThat(broker.activeConnectionCount()).isZero();
        assertThat(broker.activeIncidentCount()).isZero();
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
