package com.c2guard.bff.record;

import com.c2guard.bff.common.BffContractException;
import com.c2guard.bff.confirmation.ConfirmationStore;
import com.c2guard.bff.incident.IncidentAgentMemoryStore;
import com.c2guard.bff.incident.IncidentAnalysisSnapshotStore;
import com.c2guard.bff.movement.IncidentMovementContextStore;
import com.c2guard.bff.movement.MovementStateStore;
import com.c2guard.security.BffRole;
import com.c2guard.security.BffUserPrincipal;
import com.c2guard.security.IncidentAccessPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecordSaveServiceTest {

    @Mock
    private ResponseRecordStore recordStore;

    @Mock
    private IncidentAnalysisSnapshotStore analysisStore;

    @Mock
    private ConfirmationStore confirmationStore;

    @Mock
    private IncidentAgentMemoryStore memoryStore;

    @Mock
    private IncidentMovementContextStore movementContextStore;

    @Mock
    private MovementStateStore movementStateStore;

    @Test
    void mapsDatabaseUnavailabilityToTheRetryableRecordFailureContract() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-01T12:00:00Z"), ZoneOffset.UTC);
        RecordSaveService service = new RecordSaveService(recordStore, analysisStore,
                confirmationStore, memoryStore, movementContextStore, movementStateStore,
                new IncidentAccessPolicy(), new RecordFingerprint(), clock);
        when(analysisStore.find("ANL-DB-DOWN"))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        BffContractException error = assertThrows(BffContractException.class,
                () -> service.save("INC-DB-DOWN", request(), "REQ-DB-DOWN",
                        principal()));

        assertEquals(503, error.getStatus());
        assertEquals("RECORD_SAVE_FAILED", error.getCode());
        assertTrue(error.isRetryable());
    }

    private RecordSaveRequest request() {
        return new RecordSaveRequest(OffsetDateTime.parse("2026-08-01T11:50:00Z"),
                List.of(new RecordSaveRequest.ConversationMessage(
                        "MSG-DB-DOWN", 1, RecordSaveRequest.MessageRole.USER,
                        "저장 테스트", OffsetDateTime.parse("2026-08-01T11:51:00Z"),
                        null)), List.of("ANL-DB-DOWN"), List.of(), outcome());
    }

    private StructuredIncidentOutcome outcome() {
        return new StructuredIncidentOutcome("테스트 공장", "울산광역시",
                List.of(StructuredIncidentOutcome.PerformedAction.ZONE_CONTROL),
                StructuredIncidentOutcome.BriefApplicationStatus.APPLIED,
                List.of(),
                StructuredIncidentOutcome.FinalResponseOutcome.SPREAD_CONTAINED);
    }

    private BffUserPrincipal principal() {
        return new BffUserPrincipal("responder-db", "fire-station-119",
                Set.of(BffRole.RESPONDER), Set.of("INC-DB-DOWN"), "SID-DB",
                Instant.parse("2026-08-01T11:00:00Z"),
                Instant.parse("2026-08-01T13:00:00Z"));
    }
}
