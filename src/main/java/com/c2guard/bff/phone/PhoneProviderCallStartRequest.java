package com.c2guard.bff.phone;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record PhoneProviderCallStartRequest(
        @NotBlank @Size(max = 80) String provider,
        @NotBlank @Size(max = 160) String callId,
        @NotBlank @Size(max = 160) String eventId,
        @NotNull OffsetDateTime occurredAt
) {
}
