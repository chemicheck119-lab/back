package com.c2guard.security;

import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import static org.junit.jupiter.api.Assertions.*;

class CostAdmissionGuardTest {
    @Test void slidingQuotaExpiresAndFailureReleaseIsIdempotent() {
        var time = new MutableClock();
        var policy = new CostAdmissionProperties();
        policy.setPerPrincipalPerMinute(1);
        var guard = new CostAdmissionGuard(policy, time);
        var first = guard.tryAcquire("A");
        assertNotNull(first);
        first.close(); first.close();
        assertNull(guard.tryAcquire("A"));
        var other = guard.tryAcquire("B");
        assertNotNull(other); other.close();
        time.now += 60_000;
        assertNotNull(guard.tryAcquire("A"));
    }

    @Test void identityChurnCannotBypassGlobalQuotaOrGrowMemoryWithoutBound() {
        var policy = new CostAdmissionProperties();
        policy.setGlobalPerMinute(2);
        var guard = new CostAdmissionGuard(policy, new MutableClock());
        guard.tryAcquire("A").close(); guard.tryAcquire("B").close();
        assertNull(guard.tryAcquire("C"));
        policy.setGlobalPerMinute(100);
        policy.setMaxPrincipals(2);
        assertNull(guard.tryAcquire("C"));
    }

    private static class MutableClock extends Clock {
        long now = 1_000_000;
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return Instant.ofEpochMilli(now); }
    }
}
