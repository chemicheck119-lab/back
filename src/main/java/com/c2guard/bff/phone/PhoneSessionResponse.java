package com.c2guard.bff.phone;

import java.time.OffsetDateTime;

public record PhoneSessionResponse(
        String requestId,
        String incidentId,
        String stationId,
        String stationDisplayName,
        String status,
        OffsetDateTime createdAt
) {
}
