package com.c2guard.bff.incident;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * DB 백엔드(H2)를 실제로 태워서, 같은 사고의 첫 revision을 여러 요청이 동시에
 * 만들려고 할 때 중복 키 예외 없이 정확히 한 번씩만 revision을 받는지 검증한다
 * (Codex 리뷰 P2 — 최초 삽입 동시성 지적 대응).
 */
@SpringBootTest
class IncidentBriefRevisionStoreConcurrencyTest {

    @Autowired
    private IncidentBriefRevisionStore store;

    @Test
    void concurrentFirstRequestsForANewIncidentEachGetADistinctRevision() throws Exception {
        String incidentId = "INC-BRIEF-CONCURRENCY-" + System.nanoTime();
        int concurrency = 8;
        CyclicBarrier barrier = new CyclicBarrier(concurrency);
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        try {
            List<Callable<Long>> calls = new ArrayList<>();
            for (int index = 0; index < concurrency; index++) {
                calls.add(() -> {
                    barrier.await();
                    return store.nextRevision(incidentId);
                });
            }

            List<Long> revisions = executor.invokeAll(calls).stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception error) {
                            throw new RuntimeException(error);
                        }
                    })
                    .collect(Collectors.toList());

            assertEquals(concurrency, revisions.size());
            assertEquals(Set.copyOf(revisions).size(), revisions.size(),
                    "모든 revision은 서로 달라야 한다: " + revisions);
            assertEquals((long) concurrency, store.nextRevision(incidentId) - 1,
                    "다음 revision은 발급된 개수 + 1이어야 한다");
        } finally {
            executor.shutdownNow();
        }
    }
}
