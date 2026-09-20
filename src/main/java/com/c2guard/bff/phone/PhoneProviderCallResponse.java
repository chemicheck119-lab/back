package com.c2guard.bff.phone;

import java.time.OffsetDateTime;

public record PhoneProviderCallResponse(
        String requestId,
        String incidentId,
        String callId,
        String status,
        OffsetDateTime occurredAt,
        boolean duplicate
) {
}
