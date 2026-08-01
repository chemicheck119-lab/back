package com.c2guard.integration.model;

import com.fasterxml.jackson.databind.JsonNode;

public record ModelApiResponse(String requestId, JsonNode body) {
}
