package com.c2guard.bff.phone;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface PhoneTranscriptStore {

    Optional<StoredTranscript> findByProviderEvent(String provider, String eventId);

    default Optional<StoredTranscript> findById(String incidentId, String transcriptId) {
        return Optional.empty();
    }

    default List<StoredTranscript> findReplayEvents(String incidentId, String lastEventId,
                                                    int limit) {
        return List.of();
    }

    StoredTranscript save(String incidentId, PhoneTranscriptIngressRequest request,
                          String transcriptId, String requestId, OffsetDateTime acceptedAt);

    default StoredTranscript review(String incidentId, String transcriptId, String text,
                                    long expectedRevision, String reviewerUserId,
                                    String reviewerOrganizationId, String requestId,
                                    OffsetDateTime reviewedAt) {
        throw new UnsupportedOperationException("review is not implemented");
    }

    default StoredTranscript markAnalyzed(String incidentId, String transcriptId,
                                          long expectedRevision, String analysisId,
                                          String requestId, OffsetDateTime analyzedAt) {
        throw new UnsupportedOperationException("analysis state is not implemented");
    }

    record StoredTranscript(String transcriptId, String incidentId, String provider,
                            String callId, String providerEventId, String originalText,
                            String text, String language, boolean isFinal,
                            Integer segmentIndex, String reviewStatus, long revision,
                            String reviewedByUserId, String reviewedByOrganizationId,
                            OffsetDateTime reviewedAt, String analysisId,
                            OffsetDateTime analyzedAt, OffsetDateTime acceptedAt,
                            String requestId) {
        public StoredTranscript(String transcriptId, String incidentId, String provider,
                                String callId, String eventId, String text, String language,
                                boolean isFinal, String reviewStatus,
                                OffsetDateTime acceptedAt, String requestId) {
            this(transcriptId, incidentId, provider, callId, eventId, text, text, language,
                    isFinal, null, reviewStatus, 0L, null, null, null, null, null,
                    acceptedAt, requestId);
        }

        public String streamEventId() {
            return transcriptId + ":r" + revision;
        }
    }
}
