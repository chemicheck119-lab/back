package com.c2guard.bff.incident;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncidentAnalysisRequestMapperTest {

    private static final String REQUEST_ID = "REQ-BFF-MAPPER-0001";

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final IncidentAnalysisRequestMapper mapper = new IncidentAnalysisRequestMapper(objectMapper);

    @Test
    void mapsTheClientContractToTheModelApiContract() throws Exception {
        IncidentAnalyzeRequest source = objectMapper.readValue(
                Files.readString(Path.of("contracts/examples/bff/incident_analyze_request.json")),
                IncidentAnalyzeRequest.class);

        PreparedIncidentAnalysis prepared = mapper.prepare(source, REQUEST_ID);
        ObjectNode request = prepared.modelRequest();

        assertEquals("INC-EXAMPLE-0001", prepared.incidentId());
        assertEquals(REQUEST_ID, request.path("request_id").asText());
        assertEquals("DISPATCH_TEXT", request.path("input").path("type").asText());
        assertEquals(source.text(), request.path("input").path("text").asText());
        assertEquals("예시 사업장", request.path("location").path("facility_name").asText());
        assertEquals("DISPATCH_SYSTEM", request.path("location").path("coordinate_source").asText());
        assertEquals("화성소방서",
                request.path("operations_context").path("dispatch_station_name").asText());
        assertEquals("MDT_DEVICE_GPS", request.path("operations_context")
                .path("responder_position").path("source").asText());
        assertEquals("LineString", request.path("operations_context").path("route")
                .path("geometry").path("type").asText());
        assertTrue(request.path("operations_context").path("route").has("remaining_duration_seconds"));
        assertEquals("누출구역 통제 검토",
                request.path("planned_actions").path(0).path("raw_text").asText());
        assertEquals(5, request.path("evidence_top_k").asInt());
        assertFalse(request.toString().contains("coordinatePairValid"));
    }

    @Test
    void generatesAnIncidentIdWhenTheClientStartsANewIncident() {
        IncidentAnalyzeRequest source = new IncidentAnalyzeRequest(
                null, "염산 누출 의심", null, null, null, null, null, null);

        PreparedIncidentAnalysis prepared = mapper.prepare(source, REQUEST_ID);

        assertTrue(prepared.incidentId().startsWith("INC-BE-"));
        assertEquals(prepared.incidentId(), prepared.modelRequest().path("incident_id").asText());
    }
}
