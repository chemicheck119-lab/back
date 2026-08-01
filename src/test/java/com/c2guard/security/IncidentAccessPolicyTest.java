package com.c2guard.security;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncidentAccessPolicyTest {

    private final IncidentAccessPolicy policy = new IncidentAccessPolicy();

    @Test
    void responderCanOnlyUseExplicitIncidentAssignments() {
        BffUserPrincipal principal = principal(BffRole.RESPONDER, Set.of("INC-1"));

        assertTrue(policy.canAnalyze(principal, "INC-1"));
        assertFalse(policy.canAnalyze(principal, "INC-2"));
        assertFalse(policy.canAnalyze(principal, null));
    }

    @Test
    void wildcardScopeCanCreateAndAccessIncidents() {
        BffUserPrincipal principal = principal(BffRole.COMMANDER, Set.of("*"));

        assertTrue(policy.canAnalyze(principal, null));
        assertTrue(policy.canAccess(principal, "INC-ANY"));
    }

    @Test
    void adminRoleHasGlobalIncidentAccess() {
        BffUserPrincipal principal = principal(BffRole.ADMIN, Set.of());

        assertTrue(policy.canAnalyze(principal, null));
        assertTrue(policy.canAccess(principal, "INC-ANY"));
    }

    private BffUserPrincipal principal(BffRole role, Set<String> incidents) {
        return new BffUserPrincipal("user-1", "station-1", Set.of(role), incidents,
                "session-1", Instant.EPOCH, Instant.MAX);
    }
}
