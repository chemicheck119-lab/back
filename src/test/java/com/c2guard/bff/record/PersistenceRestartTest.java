package com.c2guard.bff.record;

import com.c2guard.bff.incident.IncidentAnalysisSnapshotStore;
import com.c2guard.security.BffRole;
import com.c2guard.security.BffUserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PersistenceRestartTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void restoresACommittedRecordAfterTheDatasourceIsClosedAndReopened() {
        String jdbcUrl = "jdbc:h2:file:" + temporaryDirectory.resolve("restart-db")
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH";
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        Clock clock = Clock.fixed(Instant.parse("2026-08-01T12:00:00Z"), ZoneOffset.UTC);
        RecordSaveRequest request = request();
        BffUserPrincipal principal = principal();
        String fingerprint = new RecordFingerprint().calculate(
                "INC-RESTART", request, principal);

        try (HikariDataSource first = dataSource(jdbcUrl)) {
            Flyway.configure().dataSource(first).load().migrate();
            JdbcTemplate jdbcTemplate = new JdbcTemplate(first);
            IncidentAnalysisSnapshotStore analysisStore =
                    new IncidentAnalysisSnapshotStore(jdbcTemplate, objectMapper, clock);
            analysisStore.save("INC-RESTART", "ANL-RESTART", "REQ-ANALYSIS",
                    objectMapper.createObjectNode(), objectMapper.createObjectNode(),
                    objectMapper.createObjectNode());
            ResponseRecordStore store = store(first, objectMapper, clock);
            store.save("INC-RESTART", request, fingerprint, "REQ-RECORD", principal,
                    List.of(analysisStore.find("ANL-RESTART").orElseThrow()), List.of(),
                    Optional.empty(), Optional.empty(), Optional.empty());
        }

        try (HikariDataSource reopened = dataSource(jdbcUrl)) {
            ResponseRecordStore restored = store(reopened, objectMapper, clock);
            StoredResponseRecord record = restored.findById("REC-RESTART")
                    .orElseThrow();
            assertEquals("INC-RESTART", record.incidentId());
            assertEquals("responder-restart", record.savedByUserId());
            assertEquals(1, restored.messageCount(record.recordId()));
        }
    }

    private HikariDataSource dataSource(String jdbcUrl) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(2);
        return new HikariDataSource(config);
    }

    private ResponseRecordStore store(HikariDataSource dataSource,
                                      ObjectMapper objectMapper, Clock clock) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        TransactionTemplate transactions = new TransactionTemplate(
                new DataSourceTransactionManager(dataSource));
        RecordIdGenerator idGenerator = new RecordIdGenerator() {
            @Override
            public String nextId() {
                return "REC-RESTART";
            }
        };
        return new ResponseRecordStore(jdbcTemplate, transactions, objectMapper,
                idGenerator, clock);
    }

    private RecordSaveRequest request() {
        return new RecordSaveRequest(
                OffsetDateTime.parse("2026-08-01T11:50:00Z"),
                List.of(new RecordSaveRequest.ConversationMessage(
                        "MSG-RESTART", 1, RecordSaveRequest.MessageRole.ASSISTANT,
                        "저장 후 재시작 복구 확인", OffsetDateTime.parse(
                        "2026-08-01T11:51:00Z"), "ANL-RESTART")),
                List.of("ANL-RESTART"), List.of(),
                new StructuredIncidentOutcome("재시작 테스트 시설", null,
                        List.of(StructuredIncidentOutcome.PerformedAction.ZONE_CONTROL),
                        StructuredIncidentOutcome.BriefApplicationStatus.NOT_REVIEWED,
                        List.of(),
                        StructuredIncidentOutcome.FinalResponseOutcome.MONITORING_CONTINUES));
    }

    private BffUserPrincipal principal() {
        return new BffUserPrincipal("responder-restart", "fire-station-119",
                Set.of(BffRole.RESPONDER), Set.of("INC-RESTART"), "SID-RESTART",
                Instant.parse("2026-08-01T11:00:00Z"),
                Instant.parse("2026-08-01T13:00:00Z"));
    }
}
