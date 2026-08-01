package com.c2guard.bff.incident;

import com.c2guard.bff.common.BffContractException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IncidentAgentMemoryStoreTest {

    private static final String INCIDENT_ID = "INC-CAS-1";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void onlyAcceptsTheNextRevisionWhoseParentIsCurrent() {
        IncidentAgentMemoryStore store = new IncidentAgentMemoryStore(objectMapper);
        ObjectNode first = response("REQ-CAS-1", null);
        store.compareAndSet(INCIDENT_ID, memory(first), events(first),
                "REQ-CAS-1", first.path("run_id").asText());

        ObjectNode winner = response("REQ-CAS-2A", memory(first));
        ObjectNode staleSibling = response("REQ-CAS-2B", memory(first));
        store.compareAndSet(INCIDENT_ID, memory(winner), events(winner),
                "REQ-CAS-2A", winner.path("run_id").asText());

        BffContractException error = assertThrows(BffContractException.class,
                () -> store.compareAndSet(INCIDENT_ID, memory(staleSibling),
                        events(staleSibling), "REQ-CAS-2B",
                        staleSibling.path("run_id").asText()));

        assertEquals(409, error.getStatus());
        assertEquals("AGENT_MEMORY_CONFLICT", error.getCode());
        assertEquals(memory(winner).path("memory_sha256").asText(),
                store.find(INCIDENT_ID).orElseThrow().memorySha256());
    }

    @Test
    void concurrentChildrenOfTheSameParentHaveExactlyOneWinner() throws Exception {
        IncidentAgentMemoryStore store = new IncidentAgentMemoryStore(objectMapper);
        ObjectNode first = response("REQ-CAS-FIRST", null);
        store.compareAndSet(INCIDENT_ID, memory(first), events(first),
                "REQ-CAS-FIRST", first.path("run_id").asText());

        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Boolean>> calls = new ArrayList<>();
            for (int index = 0; index < 16; index++) {
                String requestId = "REQ-CAS-BRANCH-" + index;
                ObjectNode branch = response(requestId, memory(first));
                calls.add(() -> {
                    try {
                        store.compareAndSet(INCIDENT_ID, memory(branch), events(branch),
                                requestId, branch.path("run_id").asText());
                        return true;
                    } catch (BffContractException conflict) {
                        assertEquals("AGENT_MEMORY_CONFLICT", conflict.getCode());
                        return false;
                    }
                });
            }

            long winners = executor.invokeAll(calls).stream()
                    .filter(future -> {
                        try {
                            return future.get();
                        } catch (Exception error) {
                            throw new RuntimeException(error);
                        }
                    })
                    .count();

            assertEquals(1, winners);
            assertEquals(2, store.find(INCIDENT_ID).orElseThrow().revision());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsMemoryWhoseChecksumWasChangedAfterValidation() {
        IncidentAgentMemoryStore store = new IncidentAgentMemoryStore(objectMapper);
        ObjectNode response = response("REQ-CAS-TAMPER", null);
        memory(response).put("revision", 2);

        BffContractException error = assertThrows(BffContractException.class,
                () -> store.compareAndSet(INCIDENT_ID, memory(response), events(response),
                        "REQ-CAS-TAMPER", response.path("run_id").asText()));

        assertEquals(422, error.getStatus());
        assertEquals("MODEL_CONTRACT_VIOLATION", error.getCode());
    }

    private ObjectNode response(String requestId, ObjectNode previousMemory) {
        ObjectNode analysis = objectMapper.createObjectNode();
        analysis.put("state", "AWAITING_SUBSTANCE_CONFIRMATION");
        analysis.put("analysis_id", "ANL-" + requestId);
        return IncidentAgentTestResponse.withAnalysis(objectMapper, analysis,
                requestId, INCIDENT_ID, previousMemory);
    }

    private static ObjectNode memory(ObjectNode response) {
        return (ObjectNode) response.path("memory");
    }

    private static com.fasterxml.jackson.databind.node.ArrayNode events(ObjectNode response) {
        return (com.fasterxml.jackson.databind.node.ArrayNode) response.path("events");
    }
}
