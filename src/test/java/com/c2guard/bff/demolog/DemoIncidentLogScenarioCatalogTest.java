package com.c2guard.bff.demolog;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoIncidentLogScenarioCatalogTest {

    @Test
    void loadsFifteenCameoBasedScenariosAcrossAllOrdinalRiskLevels() {
        DemoIncidentLogScenarioCatalog catalog = new DemoIncidentLogScenarioCatalog();
        catalog.load();

        assertEquals(15, catalog.size());
        assertEquals(15, catalog.scenarios(15).stream()
                .map(DemoIncidentLogScenarioCatalog.Scenario::scenarioId)
                .distinct().count());
        assertEquals(Set.of("LOW", "MEDIUM", "HIGH"), catalog.scenarios(15).stream()
                .map(DemoIncidentLogScenarioCatalog.Scenario::riskLevel)
                .collect(Collectors.toSet()));

        DemoIncidentLogScenarioCatalog.Scenario chlorineScenario =
                catalog.scenarios(15).get(0);
        assertEquals("7681-52-9", chlorineScenario.incidentSubstanceCas());
        assertEquals("7647-01-0", chlorineScenario.conflictSubstanceCas());
        assertEquals("HIGH", chlorineScenario.riskLevel());
        assertTrue(chlorineScenario.gasProducts().contains("Cl2"));
    }
}
