package com.c2guard.bff.movement;

import com.c2guard.bff.incident.IncidentAnalyzeRequest;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class IncidentMovementContextStoreTest {

    @Test
    void capturesOnlyTheValidatedLocationMetadataNeededByMovement() {
        IncidentMovementContextStore store = new IncidentMovementContextStore();
        OffsetDateTime resolvedAt = OffsetDateTime.parse("2026-08-01T14:20:00+09:00");
        IncidentAnalyzeRequest request = new IncidentAnalyzeRequest(
                "INC-CONTEXT", "신고 원문은 movement store에 저장하지 않습니다.",
                IncidentAnalyzeRequest.InputType.DISPATCH_TEXT, resolvedAt,
                new IncidentAnalyzeRequest.IncidentLocation(
                        "예시 사업장", "경기 화성시 팔탄면", "경기도",
                        37.2181, 126.9417,
                        IncidentAnalyzeRequest.CoordinateSource.DISPATCH_SYSTEM,
                        null, resolvedAt),
                new IncidentAnalyzeRequest.OperationsContext("화성소방서", null,
                        null, IncidentAnalyzeRequest.JourneyState.EN_ROUTE),
                List.of(), 5);

        store.capture("INC-CONTEXT", request);

        IncidentMovementContextStore.IncidentContext context =
                store.find("INC-CONTEXT").orElseThrow();
        assertEquals("예시 사업장", context.incidentPosition().label());
        assertEquals("DISPATCH_SYSTEM", context.incidentPosition().source());
        assertEquals(resolvedAt, context.incidentPosition().observedAt());
        assertEquals("화성소방서", context.responderLabel());
        assertFalse(context.incidentPosition().simulation());
    }
}
