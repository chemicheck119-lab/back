package com.c2guard.security;

import java.security.Principal;
import java.time.Instant;
import java.util.Set;

public record BffUserPrincipal(
        String userId,
        String organizationId,
        Set<BffRole> roles,
        Set<String> incidentScopes,
        String sessionId,
        Instant issuedAt,
        Instant expiresAt
) implements Principal {

    public BffUserPrincipal {
        roles = Set.copyOf(roles);
        incidentScopes = Set.copyOf(incidentScopes);
    }

    @Override
    public String getName() {
        return userId;
    }

    public boolean hasRole(BffRole role) {
        return roles.contains(role);
    }

    public boolean canAccessIncident(String incidentId) {
        return hasRole(BffRole.ADMIN)
                || incidentScopes.contains("*")
                || incidentScopes.contains(incidentId);
    }

    public boolean canCreateIncident() {
        return hasRole(BffRole.ADMIN) || incidentScopes.contains("*");
    }
}
