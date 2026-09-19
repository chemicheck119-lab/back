package com.c2guard.bff.phone;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record PhoneTranscriptIngressRequest(
        @NotBlank @Size(max = 80) String provider,
        @NotBlank @Size(max = 160) String callId,
        @NotBlank @Size(max = 160) String eventId,
        @NotNull OffsetDateTime occurredAt,
        @NotBlank @Size(max = 4000) String text,
        @Size(max = 32) String language,
        boolean isFinal,
        Integer segmentIndex
) {
}
