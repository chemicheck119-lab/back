package com.c2guard.bff.phone;

import java.time.OffsetDateTime;

public record PhoneTranscriptEvent(
        String eventId,
        String incidentId,
        String transcriptId,
        String callId,
        String text,
        String language,
        boolean isFinal,
        String reviewStatus,
        long revision,
        Integer segmentIndex,
        String reviewedBy,
        OffsetDateTime reviewedAt,
        OffsetDateTime receivedAt
) {
}
