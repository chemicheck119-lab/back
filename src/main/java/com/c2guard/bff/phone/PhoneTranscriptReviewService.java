package com.c2guard.bff.phone;

import com.c2guard.security.BffUserPrincipal;
import com.c2guard.security.IncidentAccessPolicy;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
public class PhoneTranscriptReviewService {

    private final PhoneTranscriptStore transcriptStore;
    private final PhoneTranscriptEventBroker broker;
    private final IncidentAccessPolicy incidentAccessPolicy;
    private final Clock clock;

    public PhoneTranscriptReviewService(PhoneTranscriptStore transcriptStore,
                                        PhoneTranscriptEventBroker broker,
                                        IncidentAccessPolicy incidentAccessPolicy,
                                        Clock clock) {
        this.transcriptStore = transcriptStore;
        this.broker = broker;
        this.incidentAccessPolicy = incidentAccessPolicy;
        this.clock = clock;
    }

    public PhoneTranscriptEvent review(String incidentId, String transcriptId,
                                       PhoneTranscriptReviewRequest request,
                                       BffUserPrincipal principal, String requestId) {
        incidentAccessPolicy.requireAccess(principal, incidentId);
        PhoneTranscriptStore.StoredTranscript stored = transcriptStore.review(
                incidentId, transcriptId, request.text().trim(), request.expectedRevision(),
                principal.userId(), principal.organizationId(), requestId,
                OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        return broker.publish(stored);
    }
}
