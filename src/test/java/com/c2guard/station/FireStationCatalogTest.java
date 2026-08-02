package com.c2guard.station;

import org.junit.jupiter.api.Test;

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
        assertEquals("서울", response.regions().getFirst().regionName());
        assertEquals(215, response.regions().stream()
                .mapToInt(region -> region.stations().size()).sum());

        FireStationCatalog.Station station = catalog.find("nfa-0985").orElseThrow();
        assertEquals("서울 강남소방서", station.stationDisplayName());
        assertEquals(37.5102929, station.latitude());
        assertEquals(127.06684, station.longitude());
        assertTrue(catalog.find("station-attacker").isEmpty());
    }
}
