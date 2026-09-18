package com.c2guard.bff.phone;

import java.time.OffsetDateTime;

public record PhoneTranscriptIngressResponse(
        String requestId,
        String incidentId,
        String transcriptId,
        String callId,
        boolean isFinal,
        String reviewStatus,
        OffsetDateTime acceptedAt,
        boolean duplicate
) {
}
