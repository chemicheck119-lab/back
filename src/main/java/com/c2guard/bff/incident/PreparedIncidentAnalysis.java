package com.c2guard.bff.incident;

import com.fasterxml.jackson.databind.node.ObjectNode;

record PreparedIncidentAnalysis(String incidentId, ObjectNode modelRequest) {
}
