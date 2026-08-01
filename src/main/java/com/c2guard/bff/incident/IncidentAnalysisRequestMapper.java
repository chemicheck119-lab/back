package com.c2guard.bff.incident;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.UUID;

@Component
class IncidentAnalysisRequestMapper {

    private final ObjectMapper objectMapper;

    IncidentAnalysisRequestMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    PreparedIncidentAnalysis prepare(IncidentAnalyzeRequest source, String requestId) {
        String incidentId = source.incidentId() == null || source.incidentId().isBlank()
                ? "INC-BE-" + UUID.randomUUID()
                : source.incidentId();

        ObjectNode target = objectMapper.createObjectNode();
        target.put("request_id", requestId);
        target.put("incident_id", incidentId);

        ObjectNode input = target.putObject("input");
        input.put("type", source.inputType().name());
        input.put("text", source.text().trim());
        putTime(input, "occurred_at", source.occurredAt());

        if (source.location() != null) {
            target.set("location", mapLocation(source.location()));
        }
        if (source.operationsContext() != null) {
            target.set("operations_context", mapOperationsContext(source.operationsContext()));
        }

        ArrayNode plannedActions = target.putArray("planned_actions");
        source.plannedActions().forEach(action ->
                plannedActions.addObject().put("raw_text", action));
        target.put("evidence_top_k", source.evidenceTopK());
        return new PreparedIncidentAnalysis(incidentId, target);
    }

    private ObjectNode mapLocation(IncidentAnalyzeRequest.IncidentLocation source) {
        ObjectNode target = objectMapper.createObjectNode();
        putNullable(target, "facility_name", source.facilityName());
        putNullable(target, "address", source.address());
        putNullable(target, "province", source.province());
        putNullable(target, "latitude", source.latitude());
        putNullable(target, "longitude", source.longitude());
        putNullable(target, "coordinate_source",
                source.coordinateSource() == null ? null : source.coordinateSource().name());
        putNullable(target, "geocoding_provider", source.geocodingProvider());
        putTime(target, "resolved_at", source.resolvedAt());
        return target;
    }

    private ObjectNode mapOperationsContext(IncidentAnalyzeRequest.OperationsContext source) {
        ObjectNode target = objectMapper.createObjectNode();
        putNullable(target, "dispatch_station_name", source.dispatchStationName());
        if (source.responderPosition() != null) {
            IncidentAnalyzeRequest.ResponderPosition position = source.responderPosition();
            ObjectNode responder = target.putObject("responder_position");
            responder.put("latitude", position.latitude());
            responder.put("longitude", position.longitude());
            responder.put("observed_at", position.observedAt().toString());
            responder.put("source", position.source().name());
            putNullable(responder, "accuracy_m", position.accuracyM());
        }
        if (source.route() != null && !source.route().isNull()) {
            target.set("route", toSnakeCase(source.route()));
        }
        target.put("journey_state", source.journeyState().name());
        return target;
    }

    private JsonNode toSnakeCase(JsonNode source) {
        if (source.isArray()) {
            ArrayNode target = objectMapper.createArrayNode();
            source.forEach(value -> target.add(toSnakeCase(value)));
            return target;
        }
        if (!source.isObject()) {
            return source.deepCopy();
        }
        ObjectNode target = objectMapper.createObjectNode();
        source.fields().forEachRemaining(entry ->
                target.set(camelToSnake(entry.getKey()), toSnakeCase(entry.getValue())));
        return target;
    }

    private static String camelToSnake(String value) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isUpperCase(character)) {
                result.append('_').append(Character.toLowerCase(character));
            } else {
                result.append(character);
            }
        }
        return result.toString();
    }

    private static void putTime(ObjectNode target, String field, OffsetDateTime value) {
        if (value != null) {
            target.put(field, value.toString());
        }
    }

    private static void putNullable(ObjectNode target, String field, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String text) {
            target.put(field, text);
        } else if (value instanceof Double number) {
            target.put(field, number);
        } else if (value instanceof Integer number) {
            target.put(field, number);
        } else if (value instanceof Boolean flag) {
            target.put(field, flag);
        } else {
            throw new IllegalArgumentException("Unsupported request field type: " + value.getClass());
        }
    }
}
