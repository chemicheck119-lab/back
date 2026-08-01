package com.c2guard.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class SignedSessionTokenService {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final Pattern SUBJECT = Pattern.compile("^[A-Za-z0-9_.:@-]{1,128}$");
    private static final Pattern INCIDENT = Pattern.compile("^[A-Za-z0-9_.:-]{1,128}$");
    private static final String ALGORITHM = "HmacSHA256";
    private static final int MAX_TOKEN_LENGTH = 8192;
    private static final int MAX_INCIDENT_SCOPES = 500;

    private final ObjectMapper objectMapper;
    private final BffSecurityProperties properties;
    private final Clock clock;

    public SignedSessionTokenService(ObjectMapper objectMapper,
                                     BffSecurityProperties properties,
                                     Clock clock) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.clock = clock;
    }

    public String issue(String userId, String organizationId, Set<BffRole> roles,
                        Set<String> incidentScopes) {
        return issue(userId, organizationId, organizationId, roles, incidentScopes);
    }

    public String issue(String userId, String organizationId, String stationDisplayName,
                        Set<BffRole> roles, Set<String> incidentScopes) {
        if (!properties.hasValidSessionSecret() || !properties.hasSafeCookiePolicy()) {
            throw new IllegalStateException("service session 보안 설정이 준비되지 않았습니다.");
        }
        validateSubject(userId);
        validateSubject(organizationId);
        validateStationDisplayName(stationDisplayName);
        validateRoles(roles);
        validateIncidentScopes(incidentScopes);

        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.getSessionMaxAge());
        ObjectNode header = objectMapper.createObjectNode();
        header.put("alg", "HS256");
        header.put("typ", "JWT");

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("ver", 1);
        payload.put("iss", properties.getIssuer());
        payload.put("aud", properties.getAudience());
        payload.put("sub", userId);
        payload.put("org", organizationId);
        payload.put("station_name", stationDisplayName);
        payload.put("jti", UUID.randomUUID().toString());
        payload.put("iat", issuedAt.getEpochSecond());
        payload.put("exp", expiresAt.getEpochSecond());
        ArrayNode roleArray = payload.putArray("roles");
        roles.stream().map(Enum::name).sorted().forEach(roleArray::add);
        ArrayNode incidentArray = payload.putArray("incidents");
        incidentScopes.stream().sorted().forEach(incidentArray::add);

        try {
            String encodedHeader = encode(objectMapper.writeValueAsBytes(header));
            String encodedPayload = encode(objectMapper.writeValueAsBytes(payload));
            String signingInput = encodedHeader + "." + encodedPayload;
            return signingInput + "." + encode(sign(signingInput));
        } catch (SessionTokenException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException("service session을 생성하지 못했습니다.", error);
        }
    }

    public BffUserPrincipal verify(String token) {
        try {
            if (!properties.hasValidSessionSecret() || !properties.hasSafeCookiePolicy()
                    || token == null || token.isBlank()
                    || token.length() > MAX_TOKEN_LENGTH) {
                throw new SessionTokenException();
            }
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3) {
                throw new SessionTokenException();
            }
            String signingInput = parts[0] + "." + parts[1];
            byte[] suppliedSignature = DECODER.decode(parts[2]);
            if (!MessageDigest.isEqual(sign(signingInput), suppliedSignature)) {
                throw new SessionTokenException();
            }

            JsonNode header = objectMapper.readTree(DECODER.decode(parts[0]));
            JsonNode payload = objectMapper.readTree(DECODER.decode(parts[1]));
            if (!"HS256".equals(header.path("alg").asText())
                    || !"JWT".equals(header.path("typ").asText())
                    || payload.path("ver").asInt(-1) != 1
                    || !properties.getIssuer().equals(payload.path("iss").asText())
                    || !properties.getAudience().equals(payload.path("aud").asText())) {
                throw new SessionTokenException();
            }

            String userId = requiredText(payload, "sub");
            String organizationId = requiredText(payload, "org");
            String stationDisplayName = payload.path("station_name").isTextual()
                    ? payload.path("station_name").textValue() : organizationId;
            String sessionId = requiredText(payload, "jti");
            validateSubject(userId);
            validateSubject(organizationId);
            validateStationDisplayName(stationDisplayName);
            if (!SUBJECT.matcher(sessionId).matches()) {
                throw new SessionTokenException();
            }

            long issuedAtSeconds = requiredEpoch(payload, "iat");
            long expiresAtSeconds = requiredEpoch(payload, "exp");
            Instant issuedAt = Instant.ofEpochSecond(issuedAtSeconds);
            Instant expiresAt = Instant.ofEpochSecond(expiresAtSeconds);
            validateLifetime(issuedAt, expiresAt);

            Set<BffRole> roles = parseRoles(payload.path("roles"));
            Set<String> incidentScopes = parseIncidentScopes(payload.path("incidents"));
            return new BffUserPrincipal(userId, organizationId, stationDisplayName,
                    roles, incidentScopes, sessionId, issuedAt, expiresAt);
        } catch (SessionTokenException error) {
            throw error;
        } catch (Exception error) {
            throw new SessionTokenException();
        }
    }

    private Set<BffRole> parseRoles(JsonNode node) {
        if (!node.isArray() || node.isEmpty() || node.size() > BffRole.values().length) {
            throw new SessionTokenException();
        }
        EnumSet<BffRole> roles = EnumSet.noneOf(BffRole.class);
        node.forEach(value -> {
            try {
                roles.add(BffRole.valueOf(value.asText("")));
            } catch (IllegalArgumentException error) {
                throw new SessionTokenException();
            }
        });
        validateRoles(roles);
        return Set.copyOf(roles);
    }

    private Set<String> parseIncidentScopes(JsonNode node) {
        if (!node.isArray() || node.size() > MAX_INCIDENT_SCOPES) {
            throw new SessionTokenException();
        }
        Set<String> scopes = new TreeSet<>();
        node.forEach(value -> scopes.add(value.asText("")));
        validateIncidentScopes(scopes);
        return Set.copyOf(scopes);
    }

    private void validateLifetime(Instant issuedAt, Instant expiresAt) {
        Instant now = clock.instant();
        Duration skew = properties.getClockSkew();
        if (issuedAt.isAfter(now.plus(skew))
                || !expiresAt.isAfter(now.minus(skew))
                || !expiresAt.isAfter(issuedAt)
                || Duration.between(issuedAt, expiresAt)
                .compareTo(properties.getSessionMaxAge().plus(skew)) > 0) {
            throw new SessionTokenException();
        }
    }

    private long requiredEpoch(JsonNode payload, String field) {
        JsonNode value = payload.path(field);
        if (!value.isIntegralNumber() || !value.canConvertToLong()) {
            throw new SessionTokenException();
        }
        return value.longValue();
    }

    private String requiredText(JsonNode payload, String field) {
        JsonNode value = payload.path(field);
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw new SessionTokenException();
        }
        return value.textValue();
    }

    private void validateSubject(String value) {
        if (value == null || !SUBJECT.matcher(value).matches()) {
            throw new SessionTokenException();
        }
    }

    private void validateStationDisplayName(String value) {
        if (value == null || value.isBlank() || value.length() > 120
                || value.chars().anyMatch(character -> Character.isISOControl(character))) {
            throw new SessionTokenException();
        }
    }

    private void validateRoles(Set<BffRole> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new SessionTokenException();
        }
    }

    private void validateIncidentScopes(Set<String> incidentScopes) {
        if (incidentScopes == null || incidentScopes.size() > MAX_INCIDENT_SCOPES
                || incidentScopes.stream().anyMatch(scope -> !"*".equals(scope)
                && !INCIDENT.matcher(scope).matches())) {
            throw new SessionTokenException();
        }
    }

    private String encode(byte[] value) {
        return ENCODER.encodeToString(value);
    }

    private byte[] sign(String signingInput) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(properties.signingKey(), ALGORITHM));
            return mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception error) {
            throw new SessionTokenException();
        }
    }
}
