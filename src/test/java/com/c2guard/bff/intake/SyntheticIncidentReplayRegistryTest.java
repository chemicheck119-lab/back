package com.c2guard.bff.intake;

import com.c2guard.bff.common.BffContractException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SyntheticIncidentReplayRegistryTest {

    @Test
    void expiresReplayIdsInsteadOfAcceptingThemForever() {
        IncidentReplayProperties properties = new IncidentReplayProperties();
        properties.setSyntheticIncidentTtl(Duration.ofMinutes(1));
        MutableClock clock = new MutableClock(Instant.parse("2026-08-02T03:04:05Z"));
        SyntheticIncidentReplayRegistry registry = new SyntheticIncidentReplayRegistry(
                properties, clock);
        IncidentEnvelope envelope = new IncidentReplayCatalog(clock).create(
                IncidentReplayCatalog.CONTEST_SCENARIO_ID, "REQ-REGISTRY");
        registry.register(envelope);

        assertEquals(envelope, registry.requireActive(envelope.incidentId()));
        clock.current = clock.current.plus(Duration.ofMinutes(1));
        BffContractException expired = assertThrows(BffContractException.class,
                () -> registry.requireActive(envelope.incidentId()));
        assertEquals(410, expired.getStatus());
        assertEquals("SYNTHETIC_INCIDENT_EXPIRED", expired.getCode());
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
