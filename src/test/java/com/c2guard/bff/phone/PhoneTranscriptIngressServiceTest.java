package com.c2guard.bff.phone;

import com.c2guard.bff.common.BffContractException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhoneTranscriptIngressServiceTest {

    private final PhoneIngressProperties properties = properties();
    private final Map<String, PhoneTranscriptStore.StoredTranscript> storedTranscripts = new ConcurrentHashMap<>();
    private final PhoneTranscriptStore transcriptStore = new PhoneTranscriptStore() {
        @Override
        public java.util.Optional<PhoneTranscriptStore.StoredTranscript> findByProviderEvent(String provider,
                                                                          String eventId) {
            return java.util.Optional.ofNullable(storedTranscripts.get(provider + ":" + eventId));
        }

        @Override
        public PhoneTranscriptStore.StoredTranscript save(String incidentId, PhoneTranscriptIngressRequest request,
                                     String transcriptId, String requestId,
                                     java.time.OffsetDateTime acceptedAt) {
                PhoneTranscriptStore.StoredTranscript stored = new PhoneTranscriptStore.StoredTranscript(transcriptId, incidentId, request.provider(),
                    request.callId(), request.eventId(), request.text(), request.language(),
                    request.isFinal(),
                    request.isFinal() ? "FINAL_PENDING_REVIEW" : "INTERIM",
                    acceptedAt, requestId);
                storedTranscripts.put(request.provider() + ":" + request.eventId(), stored);
                return stored;
        }
    };
    private final PhoneTranscriptEventBroker broker = new PhoneTranscriptEventBroker(transcriptStore);
    private final PhoneTranscriptIngressService service = new PhoneTranscriptIngressService(
            properties, broker, transcriptStore,
            Clock.fixed(Instant.parse("2026-09-18T12:00:00Z"), ZoneOffset.UTC));

    @Test
    void finalTranscriptRemainsPendingReview() {
        PhoneTranscriptIngressResponse response = service.accept("INC-1", request("event-1", true),
                "secret", "REQ-1");

        assertThat(response.isFinal()).isTrue();
        assertThat(response.reviewStatus()).isEqualTo("FINAL_PENDING_REVIEW");
        assertThat(response.duplicate()).isFalse();
    }

    @Test
    void duplicateProviderEventIsIdempotent() {
        PhoneTranscriptIngressRequest request = request("event-2", false);
        PhoneTranscriptIngressResponse first = service.accept("INC-1", request,
                "secret", "REQ-1");
        PhoneTranscriptIngressResponse duplicate = service.accept("INC-1", request,
                "secret", "REQ-2");

        assertThat(duplicate.duplicate()).isTrue();
        assertThat(duplicate.transcriptId()).isEqualTo(first.transcriptId());
        assertThat(duplicate.requestId()).isEqualTo("REQ-1");
        assertThat(duplicate.reviewStatus()).isEqualTo("INTERIM");
    }

    @Test
    void invalidProviderTokenIsRejected() {
        assertThatThrownBy(() -> service.accept("INC-1", request("event-3", true),
                "wrong", "REQ-1"))
                .isInstanceOf(BffContractException.class)
                .hasMessageContaining("전화 provider 인증");
    }

    @Test
    void configuredNewlineIsNormalizedButIncomingTokenStillMustMatchExactly() {
        properties.setToken("secret\r\n");
        assertThat(service.accept("INC-1", request("event-normalized", true), "secret", "REQ-1")
                .reviewStatus()).isEqualTo("FINAL_PENDING_REVIEW");
        assertThatThrownBy(() -> service.accept("INC-1", request("event-padded", true),
                " secret ", "REQ-2"))
                .isInstanceOf(BffContractException.class);
    }

    @Test
    void whitespaceOnlyConfiguredTokenFailsClosed() {
        properties.setToken(" \r\n ");
        assertThatThrownBy(() -> service.accept("INC-1", request("event-empty", true),
                "", "REQ-1"))
                .isInstanceOf(BffContractException.class)
                .hasMessageContaining("전화 provider 인증");
    }

    @Test
    void duplicateProviderEventWithDifferentPayloadFailsClosed() {
        service.accept("INC-1", request("event-conflict", true), "secret", "REQ-1");
        PhoneTranscriptIngressRequest conflicting = new PhoneTranscriptIngressRequest(
                "clawops", "call-1", "event-conflict",
                Instant.parse("2026-09-18T11:59:00Z").atOffset(ZoneOffset.UTC),
                "서로 다른 전사", "ko", true, 1);

        assertThatThrownBy(() -> service.accept("INC-1", conflicting, "secret", "REQ-2"))
                .isInstanceOf(BffContractException.class)
                .hasMessageContaining("다른 payload");
    }

    private PhoneTranscriptIngressRequest request(String eventId, boolean isFinal) {
        return new PhoneTranscriptIngressRequest("clawops", "call-1", eventId,
                Instant.parse("2026-09-18T11:59:00Z").atOffset(ZoneOffset.UTC),
                "탱크 주변에서 냄새가 납니다.", "ko", isFinal, 1);
    }

    private PhoneIngressProperties properties() {
        PhoneIngressProperties value = new PhoneIngressProperties();
        value.setEnabled(true);
        value.setToken("secret");
        return value;
    }
}
