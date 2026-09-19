package com.c2guard.bff.phone;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface PhoneTranscriptStore {

    Optional<StoredTranscript> findByProviderEvent(String provider, String eventId);

    StoredTranscript save(String incidentId, PhoneTranscriptIngressRequest request,
                          String transcriptId, String requestId, OffsetDateTime acceptedAt);

    record StoredTranscript(String transcriptId, String incidentId, String provider,
                            String callId, String eventId, String text, String language,
                            boolean isFinal, String reviewStatus, OffsetDateTime acceptedAt,
                            String requestId) {
    }
}
