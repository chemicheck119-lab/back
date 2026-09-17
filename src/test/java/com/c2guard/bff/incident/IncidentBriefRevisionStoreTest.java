package com.c2guard.bff.incident;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IncidentBriefRevisionStoreTest {

    private final IncidentBriefRevisionStore store = new IncidentBriefRevisionStore();

    @Test
    void incrementsFromOnePerIncident() {
        assertEquals(1L, store.nextRevision("INC-1"));
        assertEquals(2L, store.nextRevision("INC-1"));
        assertEquals(3L, store.nextRevision("INC-1"));
    }

    @Test
    void tracksIncidentsIndependently() {
        assertEquals(1L, store.nextRevision("INC-A"));
        assertEquals(1L, store.nextRevision("INC-B"));
        assertEquals(2L, store.nextRevision("INC-A"));
    }
}
