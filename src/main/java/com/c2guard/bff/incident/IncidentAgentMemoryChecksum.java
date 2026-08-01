package com.c2guard.bff.incident;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

final class IncidentAgentMemoryChecksum {

    private final ObjectMapper objectMapper;

    IncidentAgentMemoryChecksum(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    String calculate(JsonNode memory) {
        if (memory == null || !memory.isObject()) {
            throw new IllegalArgumentException("agent memory object is required");
        }
        ObjectNode withoutChecksum = ((ObjectNode) memory).deepCopy();
        withoutChecksum.remove("memory_sha256");
        try {
            byte[] canonical = objectMapper.writeValueAsString(sort(withoutChecksum))
                    .getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical));
        } catch (JsonProcessingException | NoSuchAlgorithmException error) {
            throw new IllegalStateException("agent memory checksum calculation failed", error);
        }
    }

    private JsonNode sort(JsonNode source) {
        if (source.isArray()) {
            ArrayNode target = objectMapper.createArrayNode();
            source.forEach(item -> target.add(sort(item)));
            return target;
        }
        if (!source.isObject()) {
            return source.deepCopy();
        }
        ObjectNode target = objectMapper.createObjectNode();
        List<Map.Entry<String, JsonNode>> fields = new ArrayList<>();
        source.fields().forEachRemaining(fields::add);
        fields.sort(Comparator.comparing(Map.Entry::getKey));
        fields.forEach(entry -> target.set(entry.getKey(), sort(entry.getValue())));
        return target;
    }
}
