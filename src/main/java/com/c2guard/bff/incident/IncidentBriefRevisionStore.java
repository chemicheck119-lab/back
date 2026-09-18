package com.c2guard.bff.incident;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 사고 하나에 대한 행동 카드(brief) 요청의 revision을 관리한다.
 * 새 분석·확인·취소 요청마다 1씩 증가하며, 모델 API가 최신 상태를 판단하는 기준이 된다
 * (docs(api) 백엔드 연동 가이드 04·07항).
 */
@Component
class IncidentBriefRevisionStore {

    private final ConcurrentMap<String, AtomicLong> revisions = new ConcurrentHashMap<>();
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    IncidentBriefRevisionStore() {
        this(null, null, Clock.systemUTC());
    }

    @Autowired
    IncidentBriefRevisionStore(JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate,
                               Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    long nextRevision(String incidentId) {
        if (jdbcTemplate != null) {
            return nextRevisionDatabase(incidentId);
        }
        return revisions.computeIfAbsent(incidentId, key -> new AtomicLong(0)).incrementAndGet();
    }

    private long nextRevisionDatabase(String incidentId) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                Long revision = transactionTemplate.execute(status ->
                        selectForUpdateThenUpsert(incidentId));
                if (revision == null) {
                    throw new IllegalStateException(
                            "incident brief revision transaction returned no value");
                }
                return revision;
            } catch (DataIntegrityViolationException concurrentFirstInsert) {
                if (attempt == 2) {
                    throw concurrentFirstInsert;
                }
                // 같은 사고의 첫 revision을 다른 트랜잭션이 방금 먼저 만들었다.
                // FOR UPDATE로 잠글 행이 없던 시점에 둘 다 INSERT를 시도해 발생하는
                // 경합이므로, 트랜잭션을 한 번 더 돌려 이번엔 그 행을 잠그고 UPDATE한다.
            }
        }
        throw new IllegalStateException("unreachable");
    }

    private long selectForUpdateThenUpsert(String incidentId) {
        List<Long> currentRows = jdbcTemplate.query("""
                        SELECT revision FROM incident_brief_revisions
                        WHERE incident_id = ?
                        FOR UPDATE
                        """, (resultSet, rowNumber) -> resultSet.getLong("revision"),
                incidentId);
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        if (currentRows.isEmpty()) {
            jdbcTemplate.update("""
                            INSERT INTO incident_brief_revisions (incident_id, revision, updated_at)
                            VALUES (?, 1, ?)
                            """, incidentId, now);
            return 1L;
        }
        long next = currentRows.get(0) + 1;
        jdbcTemplate.update("""
                        UPDATE incident_brief_revisions
                        SET revision = ?, updated_at = ?
                        WHERE incident_id = ?
                        """, next, now, incidentId);
        return next;
    }
}
