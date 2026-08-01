package com.c2guard.security;

import jakarta.servlet.http.Cookie;

import java.util.Set;

public final class BffTestSession {

    private BffTestSession() {
    }

    public static Cookie responder(SignedSessionTokenService tokenService,
                                   String... incidentScopes) {
        return cookie(tokenService, "responder-1", Set.of(BffRole.RESPONDER),
                Set.of(incidentScopes));
    }

    public static Cookie admin(SignedSessionTokenService tokenService) {
        return cookie(tokenService, "admin-1", Set.of(BffRole.ADMIN), Set.of("*"));
    }

    private static Cookie cookie(SignedSessionTokenService tokenService, String userId,
                                 Set<BffRole> roles, Set<String> incidentScopes) {
        String token = tokenService.issue(userId, "fire-station-119", roles, incidentScopes);
        return new Cookie("CHEMICHECK119_SESSION", token);
    }
}
