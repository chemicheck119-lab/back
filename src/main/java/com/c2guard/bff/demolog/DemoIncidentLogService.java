package com.c2guard.bff.demolog;

import com.c2guard.bff.common.BffContractException;
import com.c2guard.security.BffUserPrincipal;
import com.c2guard.station.FireStationCatalog;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class DemoIncidentLogService {

    private final DemoIncidentLogProperties properties;
    private final DemoIncidentLogRepository repository;
    private final DemoIncidentLogScenarioCatalog scenarioCatalog;
    private final FireStationCatalog stationCatalog;

    public DemoIncidentLogService(DemoIncidentLogProperties properties,
                                  DemoIncidentLogRepository repository,
                                  DemoIncidentLogScenarioCatalog scenarioCatalog,
                                  FireStationCatalog stationCatalog) {
        this.properties = properties;
        this.repository = repository;
        this.scenarioCatalog = scenarioCatalog;
        this.stationCatalog = stationCatalog;
    }

    public DemoIncidentLogPageResponse findForStation(
            BffUserPrincipal principal, String requestId, int offset, int limit) {
        requireEnabled();
        FireStationCatalog.Station station = authenticatedStation(principal);
        int total = repository.countByStation(
                DemoIncidentLogScenarioCatalog.DATASET_VERSION, station.stationId());
        List<DemoIncidentLogPageResponse.LogEntry> logs = repository.findByStation(
                        DemoIncidentLogScenarioCatalog.DATASET_VERSION,
                        station.stationId(), offset, limit).stream()
                .map(this::entry)
                .toList();
        return new DemoIncidentLogPageResponse(
                DemoIncidentLogPageResponse.SCHEMA_VERSION, requestId,
                DemoIncidentLogScenarioCatalog.DATASET_VERSION,
                DemoIncidentLogSeeder.DATA_CLASSIFICATION, false,
                DemoIncidentLogSeeder.DISCLOSURE,
                new DemoIncidentLogPageResponse.Station(station.stationId(),
                        station.stationDisplayName(), station.region()),
                total, offset, limit, logs);
    }

    public DemoIncidentLogCoverageResponse coverage(String requestId) {
        requireEnabled();
        String version = DemoIncidentLogScenarioCatalog.DATASET_VERSION;
        Map<String, DemoIncidentLogRepository.RegionCoverage> stored =
                repository.coverageByRegion(version);
        List<DemoIncidentLogCoverageResponse.RegionCoverage> regions =
                stationCatalog.response().regions().stream()
                        .map(region -> {
                            DemoIncidentLogRepository.RegionCoverage coverage =
                                    stored.get(region.regionName());
                            return new DemoIncidentLogCoverageResponse.RegionCoverage(
                                    region.regionName(),
                                    coverage == null ? 0 : coverage.stationCount(),
                                    coverage == null ? 0 : coverage.logCount());
                        })
                        .toList();
        return new DemoIncidentLogCoverageResponse(
                DemoIncidentLogPageResponse.SCHEMA_VERSION, requestId, version,
                DemoIncidentLogSeeder.DATA_CLASSIFICATION, false,
                DemoIncidentLogSeeder.DISCLOSURE,
                regions.size(), repository.stationCount(version),
                properties.getRecordsPerStation(), repository.countForDataset(version),
                regions);
    }

    private FireStationCatalog.Station authenticatedStation(BffUserPrincipal principal) {
        if (principal == null) {
            throw new BffContractException(401, "AUTH_REQUIRED",
                    "서명된 소방서 세션이 필요합니다.", false);
        }
        return stationCatalog.find(principal.organizationId())
                .orElseThrow(() -> new BffContractException(403,
                        "DEMO_STATION_CONTEXT_REQUIRED",
                        "전국 소방서 카탈로그에서 발급된 파일럿 세션이 필요합니다.", false));
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new BffContractException(404, "SYNTHETIC_DEMO_LOGS_DISABLED",
                    "합성 데모 로그가 활성화되지 않은 환경입니다.", false);
        }
    }

    private DemoIncidentLogPageResponse.LogEntry entry(SyntheticDemoIncidentLog log) {
        return new DemoIncidentLogPageResponse.LogEntry(
                log.demoLogId(), log.scenarioId(), log.occurredAt(),
                log.facilityName(), log.facilityAddress(),
                log.incidentSubstanceName(), log.incidentSubstanceCas(),
                log.conflictSubstanceName(), log.conflictSubstanceCas(),
                log.ruleId(), log.ruleVersion(), log.severity(), log.riskLevel(),
                log.riskLevelKo(), log.hazardCodes(), log.gasProducts(),
                log.riskSummary(), log.performedAction(), log.briefApplicationStatus(),
                log.additionalFactor(), log.finalResponseOutcome(),
                log.dataClassification(), log.operationalRecord(),
                log.sourceName(), log.sourceUrl());
    }
}
