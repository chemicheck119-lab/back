package com.c2guard.bff.record;

import java.time.OffsetDateTime;

public record StoredResponseRecord(
        String recordId,
        String incidentId,
        String fingerprint,
        OffsetDateTime conversationStartedAt,
        String savedByUserId,
        String savedByOrganizationId,
        String savedRequestId,
        OffsetDateTime savedAt,
        Integer agentMemoryRevision,
        String agentMemorySha256
) {
}
