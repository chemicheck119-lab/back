package com.c2guard.bff.substance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SubstanceDiscoveryRequestMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SubstanceDiscoveryRequestMapper mapper =
            new SubstanceDiscoveryRequestMapper(objectMapper);

    @Test
    void mapsTheClientRequestExactlyToTheModelRequestFixture() throws Exception {
        SubstanceDiscoveryRequest source = objectMapper.readValue(
                Files.readString(Path.of("contracts/examples/bff/material_discovery_request.json")),
                SubstanceDiscoveryRequest.class);
        JsonNode expected = objectMapper.readTree(Files.readString(Path.of(
                "src/test/resources/fixtures/model/material_discovery_request.json")));

        JsonNode actual = mapper.map(source);

        assertEquals(expected, actual);
    }

    @Test
    void appliesTheBffDefaultsUsedByTheCurrentClient() {
        SubstanceDiscoveryRequest source = new SubstanceDiscoveryRequest("염산", null, null);

        JsonNode actual = mapper.map(source);

        assertEquals(5, actual.path("top_k").asInt());
        assertEquals(3, actual.path("evidence_top_k").asInt());
    }
}
