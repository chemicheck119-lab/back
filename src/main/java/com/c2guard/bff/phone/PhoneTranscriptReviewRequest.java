package com.c2guard.bff.phone;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PhoneTranscriptReviewRequest(
        @NotBlank @Size(max = 4000) String text,
        @Min(0) long expectedRevision
) {
}
