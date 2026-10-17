package com.c2guard.security;

import org.springframework.stereotype.Component;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** Single-process admission control, not a monetary budget or a distributed quota. */
@Component
public class CostAdmissionGuard {
    private final CostAdmissionProperties properties;
    private final Clock clock;
    private final Map<String, ArrayDeque<Long>> principals = new HashMap<>();
    private final ArrayDeque<Long> global = new ArrayDeque<>();
    private int inFlight;

    public CostAdmissionGuard(CostAdmissionProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public synchronized Lease tryAcquire(String principal) {
        long cutoff = clock.millis() - 60_000;
        expire(global, cutoff);
        principals.values().forEach(queue -> expire(queue, cutoff));
        principals.values().removeIf(ArrayDeque::isEmpty);
        ArrayDeque<Long> queue = principals.get(principal);
        if (inFlight >= properties.getMaxInFlight()
                || global.size() >= properties.getGlobalPerMinute()
                || (queue != null && queue.size() >= properties.getPerPrincipalPerMinute())
                || (queue == null && principals.size() >= properties.getMaxPrincipals())) return null;
        queue = principals.computeIfAbsent(principal, ignored -> new ArrayDeque<>());
        long now = clock.millis();
        queue.addLast(now);
        global.addLast(now);
        inFlight++;
        return new Lease();
    }

    private static void expire(ArrayDeque<Long> queue, long cutoff) {
        while (!queue.isEmpty() && queue.peekFirst() <= cutoff) queue.removeFirst();
    }

    public final class Lease implements AutoCloseable {
        private final AtomicBoolean closed = new AtomicBoolean();
        @Override public void close() {
            if (closed.compareAndSet(false, true)) {
                synchronized (CostAdmissionGuard.this) { inFlight--; }
            }
        }
    }
}
