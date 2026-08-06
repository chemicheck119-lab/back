package com.c2guard.station;

import com.c2guard.bff.intake.IncidentEnvelope;
import com.c2guard.bff.intake.IncidentReplayCatalog;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FireStationCatalogTest {

    @Test
    void loadsTheNationalFireAgencyStationSnapshotInStableRegionalOrder() {
        FireStationCatalog catalog = new FireStationCatalog();
        catalog.load();

        FireStationCatalog.CatalogResponse response = catalog.response();
        assertEquals("chemicheck119-fire-station-catalog-v1", response.schemaVersion());
        assertEquals(17, response.regions().size());
        assertEquals("서울", response.regions().get(0).regionName());
        assertEquals(215, response.regions().stream()
                .mapToInt(region -> region.stations().size()).sum());

        FireStationCatalog.Station station = catalog.find("nfa-0985").orElseThrow();
        assertEquals("서울 강남소방서", station.stationDisplayName());
        assertEquals(37.5102929, station.latitude());
        assertEquals(127.06684, station.longitude());
        assertTrue(catalog.find("station-attacker").isEmpty());
    }

    @Test
    void everyCatalogStationProducesANearbyStationScopedSyntheticIncident() {
        FireStationCatalog stationCatalog = new FireStationCatalog();
        stationCatalog.load();
        IncidentReplayCatalog replayCatalog = new IncidentReplayCatalog(Clock.fixed(
                Instant.parse("2026-08-04T00:00:00Z"), ZoneOffset.UTC));

        stationCatalog.response().regions().stream()
                .flatMap(region -> region.stations().stream())
                .forEach(station -> {
                    IncidentEnvelope envelope = replayCatalog.create(
                            IncidentReplayCatalog.CONTEST_SCENARIO_ID,
                            "REQ-NATIONWIDE-CATALOG", station);
                    double distanceMeters = distanceMeters(
                            station.latitude(), station.longitude(),
                            envelope.location().latitude().doubleValue(),
                            envelope.location().longitude().doubleValue());

                    assertEquals(station.stationId(), envelope.stationId());
                    assertEquals(station.stationDisplayName(), envelope.stationDisplayName());
                    assertTrue(distanceMeters >= 1_190 && distanceMeters <= 1_210,
                            () -> "합성 사고 좌표가 소방서 인접 범위를 벗어남: "
                                    + station.stationDisplayName() + " / " + distanceMeters + "m");
                    assertTrue(envelope.location().latitude().doubleValue() >= 32
                                    && envelope.location().latitude().doubleValue() <= 39.5
                                    && envelope.location().longitude().doubleValue() >= 124
                                    && envelope.location().longitude().doubleValue() <= 132,
                            () -> "합성 사고 좌표가 대한민국 좌표 범위를 벗어남: "
                                    + station.stationDisplayName());
                });
    }

    private double distanceMeters(double latitudeA, double longitudeA,
                                  double latitudeB, double longitudeB) {
        double latitudeDelta = Math.toRadians(latitudeB - latitudeA);
        double longitudeDelta = Math.toRadians(longitudeB - longitudeA);
        double a = Math.sin(latitudeDelta / 2) * Math.sin(latitudeDelta / 2)
                + Math.cos(Math.toRadians(latitudeA)) * Math.cos(Math.toRadians(latitudeB))
                * Math.sin(longitudeDelta / 2) * Math.sin(longitudeDelta / 2);
        return 6_371_008.8 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
