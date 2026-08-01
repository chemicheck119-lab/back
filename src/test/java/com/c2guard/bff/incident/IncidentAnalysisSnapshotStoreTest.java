package com.c2guard.bff.incident;

import com.c2guard.bff.common.BffContractException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IncidentAnalysisSnapshotStoreTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void rejectsADuplicateAnalysisId() {
        IncidentAnalysisSnapshotStore store = new IncidentAnalysisSnapshotStore();
        store.save("INC-1", "ANL-1", "REQ-1",
                objectMapper.createObjectNode(), objectMapper.createObjectNode());

        BffContractException error = assertThrows(BffContractException.class,
                () -> store.save("INC-1", "ANL-1", "REQ-2",
                        objectMapper.createObjectNode(), objectMapper.createObjectNode()));

        assertEquals(409, error.getStatus());
        assertEquals("INCIDENT_REFERENCE_CONFLICT", error.getCode());
        assertEquals(1, store.size());
    }
}
