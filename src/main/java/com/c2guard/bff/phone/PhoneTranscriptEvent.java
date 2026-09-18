package com.c2guard.bff.phone;

import java.time.OffsetDateTime;

public record PhoneTranscriptEvent(
        long eventId,
        String incidentId,
        String transcriptId,
        String callId,
        String text,
        String language,
        boolean isFinal,
        String reviewStatus,
        OffsetDateTime receivedAt
) {
}
