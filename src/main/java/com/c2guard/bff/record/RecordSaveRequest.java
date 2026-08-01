package com.c2guard.bff.record;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;

public record RecordSaveRequest(
        @NotNull
        @JsonFormat(without = JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
        OffsetDateTime conversationStartedAt,
        @NotNull @Size(min = 1, max = 500)
        List<@NotNull @Valid ConversationMessage> messages,
        @NotNull @Size(min = 1, max = 100)
        List<@NotBlank @Size(max = 128) String> analysisIds,
        @NotNull @Size(max = 20)
        List<@NotBlank @Size(max = 128) String> confirmationIds
) {
    public enum MessageRole {
        USER,
        ASSISTANT,
        SYSTEM
    }

    public record ConversationMessage(
            @NotBlank @Size(max = 128)
            @Pattern(regexp = "^[A-Za-z0-9_.:-]{1,128}$") String messageId,
            @NotNull @Min(1) Integer sequence,
            @NotNull MessageRole role,
            @NotBlank @Size(max = 10_000) String text,
            @NotNull
            @JsonFormat(without = JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
            OffsetDateTime createdAt,
            @Size(max = 128) String analysisId
    ) {
    }
}
