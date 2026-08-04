package com.c2guard.bff.demolog;

import com.c2guard.bff.record.StructuredIncidentOutcome;
import com.c2guard.station.FireStationCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Component
public class DemoIncidentLogSeeder {

    public static final String DATA_CLASSIFICATION = "PUBLIC_SYNTHETIC";
    public static final String DISCLOSURE =
            "개인정보 없는 공개 합성 훈련 로그입니다. 실제 119 신고·출동·대원 확인·운영 기록이 아닙니다.";
    private static final String RULE_ID =
            "CAMEO-REACTIVE-GROUP-COMPATIBILITY-MATRIX";
    private static final String RULE_VERSION = "RUNTIME_MANIFEST_PINNED";
    private static final String SOURCE_NAME =
            "NOAA/EPA CAMEO Chemicals 및 소방청 전국소방서 공개 좌표";
    private static final String SOURCE_URL = "https://cameochemicals.noaa.gov/";
    private static final Instant DATASET_TIME =
            Instant.parse("2026-07-31T09:00:00Z");
    private static final Logger log = LoggerFactory.getLogger(DemoIncidentLogSeeder.class);

    private static final List<StructuredIncidentOutcome.PerformedAction> ACTIONS = List.of(
            StructuredIncidentOutcome.PerformedAction.ZONE_CONTROL,
            StructuredIncidentOutcome.PerformedAction.EVACUATION,
            StructuredIncidentOutcome.PerformedAction.LEAK_SOURCE_CONTROL,
            StructuredIncidentOutcome.PerformedAction.ADSORPTION_OR_RECOVERY,
            StructuredIncidentOutcome.PerformedAction.WATER_SPRAY_OR_DILUTION,
            StructuredIncidentOutcome.PerformedAction.VENTILATION,
            StructuredIncidentOutcome.PerformedAction.DECONTAMINATION,
            StructuredIncidentOutcome.PerformedAction.RESCUE_OR_EMS);
    private static final List<StructuredIncidentOutcome.BriefApplicationStatus> BRIEF_STATUSES =
            List.of(StructuredIncidentOutcome.BriefApplicationStatus.APPLIED,
                    StructuredIncidentOutcome.BriefApplicationStatus.PARTIALLY_APPLIED,
                    StructuredIncidentOutcome.BriefApplicationStatus.REVIEWED_NOT_APPLIED,
                    StructuredIncidentOutcome.BriefApplicationStatus.NOT_REVIEWED);
    private static final List<StructuredIncidentOutcome.AdditionalFactor> FACTORS = List.of(
            StructuredIncidentOutcome.AdditionalFactor.ENCLOSED_SPACE,
            StructuredIncidentOutcome.AdditionalFactor.HEAT_OR_PRESSURE,
            StructuredIncidentOutcome.AdditionalFactor.DRAIN_OR_WATERWAY_CONNECTION,
            StructuredIncidentOutcome.AdditionalFactor.LABEL_OR_MSDS_MISMATCH,
            StructuredIncidentOutcome.AdditionalFactor.ADDITIONAL_SUBSTANCE_FOUND,
            StructuredIncidentOutcome.AdditionalFactor.WEATHER_INFLUENCE,
            StructuredIncidentOutcome.AdditionalFactor.CASUALTY_OR_EXPOSURE,
            StructuredIncidentOutcome.AdditionalFactor.ACTUAL_MIXING_CONFIRMED);
    private static final List<StructuredIncidentOutcome.FinalResponseOutcome> OUTCOMES = List.of(
            StructuredIncidentOutcome.FinalResponseOutcome.LEAK_STOPPED,
            StructuredIncidentOutcome.FinalResponseOutcome.SPREAD_CONTAINED,
            StructuredIncidentOutcome.FinalResponseOutcome.EVACUATION_COMPLETED,
            StructuredIncidentOutcome.FinalResponseOutcome.MATERIAL_RECOVERED,
            StructuredIncidentOutcome.FinalResponseOutcome.TRANSFERRED_TO_SPECIALIST,
            StructuredIncidentOutcome.FinalResponseOutcome.MONITORING_CONTINUES);

    private final DemoIncidentLogProperties properties;
    private final DemoIncidentLogScenarioCatalog scenarioCatalog;
    private final DemoIncidentLogRepository repository;
    private final FireStationCatalog stationCatalog;

    public DemoIncidentLogSeeder(DemoIncidentLogProperties properties,
                                 DemoIncidentLogScenarioCatalog scenarioCatalog,
                                 DemoIncidentLogRepository repository,
                                 FireStationCatalog stationCatalog) {
        this.properties = properties;
        this.scenarioCatalog = scenarioCatalog;
        this.repository = repository;
        this.stationCatalog = stationCatalog;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        if (!properties.isEnabled()) return;
        List<DemoIncidentLogScenarioCatalog.Scenario> scenarios =
                scenarioCatalog.scenarios(properties.getRecordsPerStation());
        List<SyntheticDemoIncidentLog> records = new ArrayList<>(
                stationCatalog.allStations().size() * scenarios.size());
        for (int stationIndex = 0;
             stationIndex < stationCatalog.allStations().size(); stationIndex++) {
            FireStationCatalog.Station station = stationCatalog.allStations().get(stationIndex);
            for (int scenarioIndex = 0; scenarioIndex < scenarios.size(); scenarioIndex++) {
                records.add(record(station, stationIndex,
                        scenarios.get(scenarioIndex), scenarioIndex));
            }
        }
        int inserted = repository.seed(DemoIncidentLogScenarioCatalog.DATASET_VERSION, records);
        log.info("synthetic_demo_logs dataset={} stations={} records={} inserted={}",
                DemoIncidentLogScenarioCatalog.DATASET_VERSION,
                stationCatalog.allStations().size(), records.size(), inserted);
    }

    private SyntheticDemoIncidentLog record(
            FireStationCatalog.Station station,
            int stationIndex,
            DemoIncidentLogScenarioCatalog.Scenario scenario,
            int scenarioIndex) {
        int rotation = stationIndex + scenarioIndex;
        Instant occurred = DATASET_TIME
                .minus((stationIndex * 7L + scenarioIndex * 11L) % 240, ChronoUnit.DAYS)
                .minus((stationIndex * 13L + scenarioIndex * 17L) % 24, ChronoUnit.HOURS);
        return new SyntheticDemoIncidentLog(
                "DEMOLOG-" + station.stationId() + "-" + scenario.scenarioId(),
                DemoIncidentLogScenarioCatalog.DATASET_VERSION,
                scenario.scenarioId(), station.stationId(), station.stationDisplayName(),
                station.region(), OffsetDateTime.ofInstant(occurred, ZoneOffset.UTC),
                station.region() + " 공개 합성 " + scenario.facilityType(),
                station.stationDisplayName() + " 관할 공개 합성 사고지점",
                scenario.incidentSubstanceName(), scenario.incidentSubstanceCas(),
                scenario.conflictSubstanceName(), scenario.conflictSubstanceCas(),
                RULE_ID, RULE_VERSION, scenario.severity(), scenario.riskLevel(),
                scenario.riskLevelKo(), scenario.hazardCodes(), scenario.gasProducts(),
                scenario.riskSummary()
                        + ". 사고확률이 아닌 CAMEO 반응성 그룹 서수 분류이며 최종 판단은 현장 지휘관이 수행합니다.",
                ACTIONS.get(rotation % ACTIONS.size()).name(),
                BRIEF_STATUSES.get(rotation % BRIEF_STATUSES.size()).name(),
                FACTORS.get((rotation * 3) % FACTORS.size()).name(),
                OUTCOMES.get((rotation * 5) % OUTCOMES.size()).name(),
                DATA_CLASSIFICATION, false, SOURCE_NAME, SOURCE_URL, DISCLOSURE,
                OffsetDateTime.ofInstant(DATASET_TIME, ZoneOffset.UTC));
    }
}
