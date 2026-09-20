package com.c2guard.bff.phone;

import com.c2guard.bff.common.BffContractException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class PhoneTranscriptIngressService {

    private final PhoneIngressProperties properties;
    private final PhoneTranscriptEventBroker broker;
    private final PhoneTranscriptStore transcriptStore;
    private final Clock clock;

    public PhoneTranscriptIngressService(PhoneIngressProperties properties,
                                         PhoneTranscriptEventBroker broker,
                                         PhoneTranscriptStore transcriptStore,
                                         Clock clock) {
        this.properties = properties;
        this.broker = broker;
                this.transcriptStore = transcriptStore;
        this.clock = clock;
    }

    public PhoneTranscriptIngressResponse accept(String incidentId,
                                                 PhoneTranscriptIngressRequest request,
                                                 String ingressToken,
                                                 String requestId) {
        if (!properties.isEnabled()) {
            throw new BffContractException(404, "PHONE_INGRESS_DISABLED",
                    "전화 transcript 수신이 활성화되지 않았습니다.", false);
        }
        if (properties.getToken().isBlank()
                || ingressToken == null
                || !MessageDigest.isEqual(properties.getToken().getBytes(StandardCharsets.UTF_8),
                ingressToken.getBytes(StandardCharsets.UTF_8))) {
            throw new BffContractException(401, "PHONE_INGRESS_UNAUTHORIZED",
                    "전화 provider 인증이 필요합니다.", false);
        }
        if (request.text().length() > properties.getMaxTextLength()) {
            throw new BffContractException(413, "PHONE_TRANSCRIPT_TOO_LARGE",
                    "전화 transcript가 허용된 길이를 초과했습니다.", false);
        }
        var existing = transcriptStore.findByProviderEvent(request.provider(), request.eventId());
        if (existing.isPresent()) {
            var stored = existing.get();
            if (!stored.incidentId().equals(incidentId)
                    || !stored.callId().equals(request.callId())
                    || !stored.originalText().equals(request.text())
                    || stored.isFinal() != request.isFinal()) {
                throw new BffContractException(409, "PHONE_EVENT_ID_CONFLICT",
                        "같은 provider event ID에 다른 payload가 수신되었습니다.", false);
            }
            return new PhoneTranscriptIngressResponse(stored.requestId(), stored.incidentId(),
                    stored.transcriptId(), stored.callId(), stored.isFinal(),
                    stored.reviewStatus(), stored.revision(), stored.acceptedAt(), true);
        }
        OffsetDateTime acceptedAt = OffsetDateTime.now(clock);
        String transcriptId = "TRX-" + UUID.randomUUID();
        var stored = transcriptStore.save(incidentId, request, transcriptId, requestId, acceptedAt);
        boolean created = transcriptId.equals(stored.transcriptId());
        if (created) {
            broker.publish(stored);
        }
        return new PhoneTranscriptIngressResponse(stored.requestId(), stored.incidentId(),
                stored.transcriptId(), stored.callId(), stored.isFinal(), stored.reviewStatus(),
                stored.revision(), stored.acceptedAt(), !created);
    }
}
