package com.c2guard.bff.confirmation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record ConfirmationRequest(
        @NotNull ConfirmationRole role,
        @NotNull @Size(min = 5, max = 12)
        @Pattern(regexp = "^[0-9]{2,7}-[0-9]{2}-[0-9]$") String casNumber,
        @Size(max = 160) String displayName,
        @NotNull ConfirmationBasis confirmationBasis,
        @NotNull
        @JsonFormat(without = JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
        OffsetDateTime observedAt
) {
    public ConfirmationRequest {
        casNumber = casNumber == null ? null : casNumber.trim();
        displayName = displayName == null || displayName.isBlank()
                ? null : displayName.trim();
    }

    @AssertTrue(message = "CAS Registry Number check digit가 올바르지 않습니다.")
    @JsonIgnore
    public boolean isCasCheckDigitValid() {
        return casNumber == null || CasNumberValidator.isValid(casNumber);
    }
}
