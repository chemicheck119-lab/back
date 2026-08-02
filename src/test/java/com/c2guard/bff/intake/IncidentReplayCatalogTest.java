package com.c2guard.bff.intake;

import com.c2guard.bff.common.BffContractException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IncidentReplayCatalogTest {

    private final IncidentReplayCatalog catalog = new IncidentReplayCatalog(
            Clock.fixed(Instant.parse("2026-08-02T03:04:05.678Z"), ZoneOffset.UTC));

    @Test
    void createsATraceablePiiFreePublicSyntheticEnvelope() {
        IncidentEnvelope envelope = catalog.create(
                IncidentReplayCatalog.CONTEST_SCENARIO_ID, "REQ-REPLAY-001");

        assertEquals("chemicheck119-incident-envelope-v1", envelope.schemaVersion());
        assertEquals(IncidentSourceType.SYNTHETIC_DISPATCH_REPLAY, envelope.sourceType());
        assertEquals(IncidentDataClassification.PUBLIC_SYNTHETIC,
                envelope.dataClassification());
        assertEquals("REQ-REPLAY-001", envelope.requestId());
        assertEquals("STATION-PUBLIC-DEMO", envelope.stationId());
        assertEquals("공개 합성 사업장", envelope.facilityName());
        assertEquals(Instant.parse("2026-08-02T03:03:20.678Z"), envelope.occurredAt());
        assertFalse(envelope.containsPersonalInformation());
        assertEquals(1, envelope.datasetReferences().size());
    }

    @Test
    void rejectsUnknownScenariosWithoutFallingBack() {
        BffContractException error = assertThrows(BffContractException.class,
                () -> catalog.create("unknown", "REQ-REPLAY-404"));

        assertEquals(404, error.getStatus());
        assertEquals("REPLAY_SCENARIO_NOT_FOUND", error.getCode());
    }
}
