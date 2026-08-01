package com.c2guard.security;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;

@Component
public class PublicAnalysisPrincipalProvider {

    private static final String USER_ID = "public-fe";
    private static final String STATION_ID = "public-staging";
    private static final String STATION_DISPLAY_NAME = "공개 웹 분석";
    private static final String SESSION_ID = "public-analysis";

    private final BffSecurityProperties properties;
    private final Clock clock;

    public PublicAnalysisPrincipalProvider(BffSecurityProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public BffUserPrincipal resolve(BffUserPrincipal authenticatedPrincipal) {
        if (authenticatedPrincipal != null) {
            return authenticatedPrincipal;
        }
        if (!properties.isPublicAnalysisEnabled()) {
            throw new IllegalStateException("public analysis is not enabled");
        }
        Instant issuedAt = clock.instant();
        return new BffUserPrincipal(USER_ID, STATION_ID, STATION_DISPLAY_NAME,
                Set.of(BffRole.RESPONDER), Set.of("*"), SESSION_ID,
                issuedAt, issuedAt.plus(properties.getSessionMaxAge()));
    }
}
