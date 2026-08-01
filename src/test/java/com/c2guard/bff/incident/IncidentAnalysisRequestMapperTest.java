package com.c2guard.bff.incident;

import com.c2guard.bff.confirmation.ConfirmationBasis;
import com.c2guard.bff.confirmation.ConfirmationRole;
import com.c2guard.bff.confirmation.ConfirmationStatus;
import com.c2guard.bff.confirmation.SubstanceConfirmation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;

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

    @Test
    void mapsOnlyAuthoritativeConfirmationRecordsToTheModelContract() throws Exception {
        IncidentAnalyzeRequest source = new IncidentAnalyzeRequest(
                "INC-EXAMPLE-0001", "확인된 두 물질 재분석", null,
                null, null, null, null, null);
        PreparedIncidentAnalysis prepared = mapper.prepare(source, "REQ-EXAMPLE-0002");
        mapper.addActiveConfirmations(prepared.modelRequest(), Map.of(
                ConfirmationRole.INCIDENT, confirmation(
                        "CFM-INC-0001", ConfirmationRole.INCIDENT, "7681-52-9",
                        "차아염소산나트륨", ConfirmationBasis.CONTAINER_LABEL,
                        "2026-01-15T14:25:00+09:00"),
                ConfirmationRole.FACILITY, confirmation(
                        "CFM-FAC-0001", ConfirmationRole.FACILITY, "7647-01-0",
                        "염산", ConfirmationBasis.SITE_MSDS,
                        "2026-01-15T14:27:00+09:00")));

        JsonNode expected = objectMapper.readTree(Files.readString(Path.of(
                "src/test/resources/fixtures/model/incident_confirmed_request.json")));
        assertEquals(expected.path("confirmed_incident_substance"),
                prepared.modelRequest().path("confirmed_incident_substance"));
        assertEquals(expected.path("confirmed_facility_substance"),
                prepared.modelRequest().path("confirmed_facility_substance"));
    }

    private SubstanceConfirmation confirmation(
            String id, ConfirmationRole role, String casNumber, String displayName,
            ConfirmationBasis basis, String observedAt) {
        return new SubstanceConfirmation(
                id, "INC-EXAMPLE-0001", role, casNumber, displayName, basis,
                OffsetDateTime.parse(observedAt), "responder-1", "station-1",
                Instant.parse("2026-01-15T05:30:00Z"), "REQ-CONFIRM", 1,
                ConfirmationStatus.ACTIVE, null, null);
    }
}
