package com.c2guard.bff.confirmation;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfirmationStoreTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-01T00:00:00Z");

    @Test
    void exactDuplicateIsIdempotentAndKeepsTheOriginalAuditRecord() {
        ConfirmationStore store = store();
        ConfirmationSaveCommand first = command("REQ-1", "user-1", "7681-52-9", "차아염소산나트륨");
        ConfirmationSaveCommand duplicate = command("REQ-2", "user-2", "7681-52-9", "차아염소산나트륨");

        ConfirmationSaveResult created = store.save(first);
        ConfirmationSaveResult repeated = store.save(duplicate);

        assertTrue(created.created());
        assertFalse(repeated.created());
        assertEquals(created.confirmation().confirmationId(),
                repeated.confirmation().confirmationId());
        assertEquals("user-1", repeated.confirmation().confirmedByUserId());
        assertEquals("REQ-1", repeated.confirmation().createdRequestId());
        assertEquals(1, store.history("INC-1", ConfirmationRole.INCIDENT).size());
    }

    @Test
    void correctionAppendsARevisionAndSupersedesWithoutDeletingHistory() {
        ConfirmationStore store = store();

        SubstanceConfirmation first = store.save(command(
                "REQ-1", "user-1", "7681-52-9", "차아염소산나트륨")).confirmation();
        SubstanceConfirmation corrected = store.save(command(
                "REQ-2", "user-2", "7647-01-0", "염산")).confirmation();
        List<SubstanceConfirmation> history = store.history(
                "INC-1", ConfirmationRole.INCIDENT);

        assertEquals(2, history.size());
        assertEquals(ConfirmationStatus.SUPERSEDED, history.get(0).status());
        assertEquals(corrected.confirmationId(), history.get(0).supersededByConfirmationId());
        assertEquals(2, corrected.revision());
        assertEquals(ConfirmationStatus.ACTIVE, corrected.status());
        assertEquals(corrected, store.findActive("INC-1", ConfirmationRole.INCIDENT)
                .orElseThrow());
        assertEquals(ConfirmationStatus.SUPERSEDED,
                store.findById(first.confirmationId()).orElseThrow().status());
    }

    @Test
    void concurrentExactDuplicatesCreateOneAuthoritativeRecord() throws Exception {
        ConfirmationStore store = store();
        int attempts = 24;
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<ConfirmationSaveResult>> futures = new ArrayList<>();
            for (int index = 0; index < attempts; index++) {
                int request = index;
                futures.add(executor.submit(() -> {
                    start.await();
                    return store.save(command("REQ-" + request, "user-" + request,
                            "7681-52-9", "차아염소산나트륨"));
                }));
            }
            start.countDown();
            Set<String> ids = new java.util.HashSet<>();
            int created = 0;
            for (Future<ConfirmationSaveResult> future : futures) {
                ConfirmationSaveResult result = future.get();
                ids.add(result.confirmation().confirmationId());
                if (result.created()) {
                    created++;
                }
            }

            assertEquals(1, ids.size());
            assertEquals(1, created);
            assertEquals(1, store.history("INC-1", ConfirmationRole.INCIDENT).size());
        } finally {
            executor.shutdownNow();
        }
    }

    private ConfirmationStore store() {
        AtomicInteger sequence = new AtomicInteger();
        ConfirmationIdGenerator generator = new ConfirmationIdGenerator() {
            @Override
            public String nextId() {
                return "CNF-TEST-" + sequence.incrementAndGet();
            }
        };
        return new ConfirmationStore(generator,
                Clock.fixed(CREATED_AT, ZoneOffset.UTC));
    }

    private ConfirmationSaveCommand command(String requestId, String userId,
                                            String casNumber, String displayName) {
        return new ConfirmationSaveCommand(
                "INC-1", ConfirmationRole.INCIDENT, casNumber, displayName,
                ConfirmationBasis.CONTAINER_LABEL,
                OffsetDateTime.parse("2026-07-31T14:25:00+09:00"),
                userId, "station-1", requestId);
    }
}
