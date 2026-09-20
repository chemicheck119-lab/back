package com.c2guard.bff.phone;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record PhoneProviderCallEndRequest(
        @NotBlank @Size(max = 80) String provider,
        @NotBlank @Size(max = 160) String eventId,
        @NotNull OffsetDateTime occurredAt,
        @NotBlank @Pattern(regexp = "^(completed|failed|canceled|rejected|busy|no-answer|unknown)$")
        String status
) {
}
