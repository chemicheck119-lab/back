package com.c2guard.bff.record;

import java.time.OffsetDateTime;

public record RecordSaveResponse(
        String schemaVersion,
        String requestId,
        String incidentId,
        String recordId,
        OffsetDateTime savedAt,
        boolean resetAllowed
) {
    public RecordSaveResponse(String requestId, String incidentId,
                              String recordId, OffsetDateTime savedAt) {
        this("chemicheck119-dashboard-bff-v1", requestId, incidentId,
                recordId, savedAt, true);
    }
}
