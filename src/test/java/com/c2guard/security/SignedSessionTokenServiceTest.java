package com.c2guard.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignedSessionTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");

    @Test
    void verifiesAnHs256SessionAndPreservesLeastPrivilegeClaims() {
        BffSecurityProperties properties = properties();
        SignedSessionTokenService service = service(properties, NOW);

        String token = service.issue("responder-1", "station-1",
                Set.of(BffRole.RESPONDER), Set.of("INC-1"));
        BffUserPrincipal principal = service.verify(token);

        assertEquals("responder-1", principal.userId());
        assertEquals("station-1", principal.organizationId());
        assertEquals(Set.of(BffRole.RESPONDER), principal.roles());
        assertEquals(Set.of("INC-1"), principal.incidentScopes());
        assertTrue(principal.canAccessIncident("INC-1"));
    }

    @Test
    void preservesTheTrustedStationDisplayName() {
        SignedSessionTokenService service = service(properties(), NOW);

        String token = service.issue("responder-1", "station-1", "서울 테스트 소방서",
                Set.of(BffRole.RESPONDER), Set.of("INC-1"));

        assertEquals("서울 테스트 소방서",
                service.verify(token).stationDisplayName());
    }

    @Test
    void rejectsSignatureTampering() {
        SignedSessionTokenService service = service(properties(), NOW);
        String token = service.issue("responder-1", "station-1",
                Set.of(BffRole.RESPONDER), Set.of("INC-1"));
        int signatureStart = token.lastIndexOf('.') + 1;
        char replacement = token.charAt(signatureStart) == 'A' ? 'B' : 'A';
        String tampered = token.substring(0, signatureStart) + replacement
                + token.substring(signatureStart + 1);

        assertThrows(SessionTokenException.class,
                () -> service.verify(tampered));
    }

    @Test
    void rejectsExpiredSessionsOutsideTheClockSkew() {
        BffSecurityProperties properties = properties();
        properties.setSessionMaxAge(Duration.ofHours(1));
        String token = service(properties, NOW).issue("responder-1", "station-1",
                Set.of(BffRole.RESPONDER), Set.of("INC-1"));

        SignedSessionTokenService later = service(properties, NOW.plus(Duration.ofHours(2)));
        assertThrows(SessionTokenException.class, () -> later.verify(token));
    }

    @Test
    void rejectsTokensForAnotherAudience() {
        BffSecurityProperties issuerProperties = properties();
        String token = service(issuerProperties, NOW).issue("responder-1", "station-1",
                Set.of(BffRole.RESPONDER), Set.of("INC-1"));
        BffSecurityProperties verifierProperties = properties();
        verifierProperties.setAudience("another-service");

        assertThrows(SessionTokenException.class,
                () -> service(verifierProperties, NOW).verify(token));
    }

    @Test
    void refusesToIssueWithAShortSigningSecret() {
        BffSecurityProperties properties = new BffSecurityProperties();
        properties.setSessionSecret("short-secret");

        assertThrows(IllegalStateException.class,
                () -> service(properties, NOW).issue("responder-1", "station-1",
                        Set.of(BffRole.RESPONDER), Set.of("INC-1")));
    }

    @Test
    void refusesToAuthenticateWithAnInsecureCrossSiteCookiePolicy() {
        String token = service(properties(), NOW).issue("responder-1", "station-1",
                Set.of(BffRole.RESPONDER), Set.of("INC-1"));
        BffSecurityProperties unsafe = properties();
        unsafe.setCookieSameSite("None");
        unsafe.setCookieSecure(false);

        assertThrows(SessionTokenException.class,
                () -> service(unsafe, NOW).verify(token));
    }

    private SignedSessionTokenService service(BffSecurityProperties properties, Instant now) {
        return new SignedSessionTokenService(new ObjectMapper(), properties,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private BffSecurityProperties properties() {
        BffSecurityProperties properties = new BffSecurityProperties();
        properties.setSessionSecret("test-only-session-secret-at-least-32-bytes-long");
        return properties;
    }
}
