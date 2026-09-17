package com.c2guard.bff.incident;

import com.c2guard.bff.common.BffContractException;
import com.c2guard.bff.confirmation.ConfirmationStore;
import com.c2guard.integration.model.ModelApiClient;
import com.c2guard.integration.model.ModelApiResponse;
import com.c2guard.security.BffRole;
import com.c2guard.security.BffUserPrincipal;
import com.c2guard.security.IncidentAccessPolicy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IncidentBriefBffServiceTest {

    private static final String REQUEST_ID = "REQ-BRIEF-0001";

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final ModelApiClient modelApiClient = mock(ModelApiClient.class);
    private final ConfirmationStore confirmationStore = mock(ConfirmationStore.class);
    private final IncidentAccessPolicy incidentAccessPolicy = mock(IncidentAccessPolicy.class);
    private final IncidentBriefBffService service = new IncidentBriefBffService(
            modelApiClient,
            new IncidentAnalysisRequestMapper(objectMapper),
            new IncidentBriefRequestMapper(objectMapper),
            new IncidentBriefRevisionStore(),
            confirmationStore,
            incidentAccessPolicy);

    private final BffUserPrincipal principal = new BffUserPrincipal("responder-1",
            "fire-station-119", Set.of(BffRole.RESPONDER), Set.of("*"), "session-1",
            Instant.now(), Instant.now().plusSeconds(3600));

    @BeforeEach
    void setUp() {
        when(confirmationStore.findActiveForIncident(any())).thenReturn(Map.of());
    }

    @Test
    void wrapsAnalysisAndAssignsIncrementingRevisionPerIncident() {
        when(modelApiClient.briefIncident(any(), eq(REQUEST_ID)))
                .thenReturn(new ModelApiResponse(REQUEST_ID,
                        objectMapper.createObjectNode().put("schema_version", "action-brief-v1")));
        IncidentAnalyzeRequest request = new IncidentAnalyzeRequest("INC-BRIEF-1",
                "차아염소산나트륨 탱크에서 누출이 있습니다.", null, null, null, null, null, null);

        service.brief(new IncidentBriefCommand(request, List.of(), null), REQUEST_ID, principal);
        service.brief(new IncidentBriefCommand(request, List.of(), null), REQUEST_ID, principal);

        ArgumentCaptor<JsonNode> sentRequests = ArgumentCaptor.forClass(JsonNode.class);
        verify(modelApiClient, times(2)).briefIncident(sentRequests.capture(), eq(REQUEST_ID));

        assertEquals(1L, sentRequests.getAllValues().get(0).path("revision").asLong());
        assertEquals(2L, sentRequests.getAllValues().get(1).path("revision").asLong());
        assertEquals("INC-BRIEF-1",
                sentRequests.getAllValues().get(0).path("analysis").path("incident_id").asText());
        assertEquals(REQUEST_ID,
                sentRequests.getAllValues().get(0).path("analysis").path("request_id").asText());
        verify(incidentAccessPolicy, times(2)).requireAnalyze(principal, "INC-BRIEF-1");
    }

    @Test
    void throwsContractViolationWhenModelEchoesADifferentRequestId() {
        when(modelApiClient.briefIncident(any(), eq(REQUEST_ID)))
                .thenReturn(new ModelApiResponse("REQ-OTHER", objectMapper.createObjectNode()));
        IncidentAnalyzeRequest request = new IncidentAnalyzeRequest("INC-BRIEF-2",
                "염산 누출이 있습니다.", null, null, null, null, null, null);

        BffContractException error = assertThrows(BffContractException.class,
                () -> service.brief(new IncidentBriefCommand(request, List.of(), null),
                        REQUEST_ID, principal));

        assertEquals("MODEL_CONTRACT_VIOLATION", error.getCode());
        assertEquals(422, error.getStatus());
    }
}
