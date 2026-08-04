package com.c2guard.bff.demolog;

import java.util.List;

public record DemoIncidentLogCoverageResponse(
        String schemaVersion,
        String requestId,
        String datasetVersion,
        String dataClassification,
        boolean operationalRecord,
        String disclosure,
        int regionCount,
        int stationCount,
        int scenarioCount,
        int totalLogCount,
        List<RegionCoverage> regions
) {
    public record RegionCoverage(String region, int stationCount, int logCount) {
    }
}
