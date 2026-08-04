package com.c2guard.bff.demolog;

import jakarta.annotation.PostConstruct;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class DemoIncidentLogScenarioCatalog {

    public static final String DATASET_VERSION = "synthetic-demo-log-v1";
    private static final String RESOURCE_PATH =
            "data/synthetic-demo-log-scenarios-v1.csv";
    private static final Set<String> RISK_LEVELS = Set.of("LOW", "MEDIUM", "HIGH");

    private List<Scenario> scenarios = List.of();

    @PostConstruct
    void load() {
        List<Scenario> loaded = new ArrayList<>();
        Set<String> scenarioIds = new HashSet<>();
        ClassPathResource resource = new ClassPathResource(RESOURCE_PATH);
        try (InputStreamReader reader = new InputStreamReader(
                resource.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .build()
                     .parse(reader)) {
            for (CSVRecord row : parser) {
                Scenario scenario = new Scenario(
                        required(row, "scenario_id"),
                        required(row, "facility_type"),
                        required(row, "incident_substance_name"),
                        required(row, "incident_substance_cas"),
                        required(row, "conflict_substance_name"),
                        required(row, "conflict_substance_cas"),
                        required(row, "severity"),
                        required(row, "risk_level"),
                        required(row, "risk_level_ko"),
                        codes(row.get("hazard_codes")),
                        codes(row.get("gas_products")),
                        required(row, "risk_summary"));
                if (!scenarioIds.add(scenario.scenarioId())
                        || !RISK_LEVELS.contains(scenario.riskLevel())) {
                    throw new IllegalStateException(
                            "합성 데모 시나리오 식별자 또는 위험 등급이 올바르지 않습니다.");
                }
                loaded.add(scenario);
            }
        } catch (IOException error) {
            throw new IllegalStateException("합성 데모 시나리오를 읽지 못했습니다.", error);
        }
        if (loaded.size() != 15) {
            throw new IllegalStateException("합성 데모 시나리오는 정확히 15건이어야 합니다.");
        }
        scenarios = List.copyOf(loaded);
    }

    public List<Scenario> scenarios(int limit) {
        if (limit < 1 || limit > scenarios.size()) {
            throw new IllegalArgumentException("합성 데모 시나리오 개수가 올바르지 않습니다.");
        }
        return scenarios.subList(0, limit);
    }

    public int size() {
        return scenarios.size();
    }

    private String required(CSVRecord row, String column) {
        String value = row.get(column).trim();
        if (value.isEmpty()) {
            throw new IllegalStateException("합성 데모 시나리오 필수값이 비어 있습니다: " + column);
        }
        return value;
    }

    private List<String> codes(String value) {
        if (value == null || value.isBlank()) return List.of();
        return List.of(value.split("\\|", -1)).stream()
                .map(String::trim)
                .filter(code -> !code.isEmpty())
                .distinct()
                .toList();
    }

    public record Scenario(
            String scenarioId,
            String facilityType,
            String incidentSubstanceName,
            String incidentSubstanceCas,
            String conflictSubstanceName,
            String conflictSubstanceCas,
            String severity,
            String riskLevel,
            String riskLevelKo,
            List<String> hazardCodes,
            List<String> gasProducts,
            String riskSummary
    ) {
    }
}
