package com.c2guard.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

@Component
public class IncidentPathAuthorizationManager
        implements AuthorizationManager<RequestAuthorizationContext> {

    private static final String PREFIX = "/api/c2guard/v1/incidents/";

    private final IncidentAccessPolicy incidentAccessPolicy;

    public IncidentPathAuthorizationManager(IncidentAccessPolicy incidentAccessPolicy) {
        this.incidentAccessPolicy = incidentAccessPolicy;
    }

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication,
                                       RequestAuthorizationContext context) {
        Authentication current = authentication.get();
        String incidentId = extractIncidentId(context.getRequest().getRequestURI());
        boolean granted = current != null && current.isAuthenticated()
                && !(current instanceof AnonymousAuthenticationToken)
                && current.getPrincipal() instanceof BffUserPrincipal principal
                && incidentAccessPolicy.canAccess(principal, incidentId);
        return new AuthorizationDecision(granted);
    }

    private String extractIncidentId(String uri) {
        if (!uri.startsWith(PREFIX)) {
            return null;
        }
        String remainder = uri.substring(PREFIX.length());
        int separator = remainder.indexOf('/');
        if (separator <= 0) {
            return null;
        }
        return URLDecoder.decode(remainder.substring(0, separator), StandardCharsets.UTF_8);
    }
}
