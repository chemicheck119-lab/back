package com.c2guard.bff.record;

import java.util.List;

public record RecordListResponse(
        String schemaVersion,
        String requestId,
        List<RecordSummary> records
) {
    public RecordListResponse(String requestId, List<RecordSummary> records) {
        this("chemicheck119-dashboard-bff-v1", requestId, records);
    }
}
