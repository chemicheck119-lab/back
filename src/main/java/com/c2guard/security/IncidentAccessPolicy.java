package com.c2guard.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class IncidentAccessPolicy {

    private static final Set<BffRole> OPERATIONAL_ROLES = Set.of(
            BffRole.RESPONDER, BffRole.COMMANDER, BffRole.ADMIN);

    public boolean canAccess(BffUserPrincipal principal, String incidentId) {
        return principal != null && hasOperationalRole(principal)
                && incidentId != null && !incidentId.isBlank()
                && principal.canAccessIncident(incidentId);
    }

    public boolean canAnalyze(BffUserPrincipal principal, String incidentId) {
        if (principal == null || !hasOperationalRole(principal)) {
            return false;
        }
        return incidentId == null || incidentId.isBlank()
                ? principal.canCreateIncident()
                : principal.canAccessIncident(incidentId);
    }

    public void requireAnalyze(BffUserPrincipal principal, String incidentId) {
        if (!canAnalyze(principal, incidentId)) {
            throw new AccessDeniedException("incident access denied");
        }
    }

    private boolean hasOperationalRole(BffUserPrincipal principal) {
        return principal.roles().stream().anyMatch(OPERATIONAL_ROLES::contains);
    }
}
