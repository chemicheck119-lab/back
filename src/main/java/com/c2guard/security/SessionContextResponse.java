package com.c2guard.security;

import java.time.Instant;
import java.util.List;

public record SessionContextResponse(
        String schemaVersion,
        String requestId,
        String userId,
        String stationId,
        String stationDisplayName,
        List<String> roles,
        List<String> incidentScopes,
        Instant issuedAt,
        Instant expiresAt
) {
    public SessionContextResponse(String requestId, BffUserPrincipal principal) {
        this("chemicheck119-dashboard-bff-v1", requestId, principal.userId(),
                principal.organizationId(), principal.stationDisplayName(),
                principal.roles().stream().map(Enum::name).sorted().toList(),
                principal.incidentScopes().stream().sorted().toList(),
                principal.issuedAt(), principal.expiresAt());
    }
}
