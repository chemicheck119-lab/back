package com.c2guard.auth.staging;

import com.c2guard.security.BffRole;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.regex.Pattern;

@Component
@ConfigurationProperties(prefix = "chemicheck119.staging-auth")
public class StagingAuthProperties {

    private static final Pattern ID = Pattern.compile("^[A-Za-z0-9_.:@-]{1,128}$");

    private boolean enabled;
    private String callbackUrl = "";
    private String userId = "";
    private String stationId = "";
    private String stationDisplayName = "";
    private String password = "";
    private Set<BffRole> roles = Set.of(BffRole.RESPONDER);
    private Set<String> incidentScopes = Set.of("*");
    private Duration csrfMaxAge = Duration.ofMinutes(5);
    private Duration lockDuration = Duration.ofMinutes(10);
    private int maxFailedAttempts = 5;

    public boolean isReady() {
        return !enabled || validId(userId) && validId(stationId)
                && stationDisplayName != null && !stationDisplayName.isBlank()
                && stationDisplayName.length() <= 120
                && stationDisplayName.chars().noneMatch(Character::isISOControl)
                && password.getBytes(StandardCharsets.UTF_8).length >= 16
                && roles != null && !roles.isEmpty()
                && incidentScopes != null && !incidentScopes.isEmpty()
                && incidentScopes.stream().allMatch(scope -> "*".equals(scope)
                || ID.matcher(scope).matches())
                && validCallback(callbackUrl)
                && positiveBounded(csrfMaxAge, Duration.ofMinutes(30))
                && positiveBounded(lockDuration, Duration.ofHours(1))
                && maxFailedAttempts >= 1 && maxFailedAttempts <= 20;
    }

    public URI callbackUri() {
        if (!validCallback(callbackUrl)) {
            throw new IllegalStateException("staging auth callback URL이 올바르지 않습니다.");
        }
        return URI.create(callbackUrl);
    }

    boolean credentialsMatch(String candidateUserId, String candidatePassword) {
        if (candidateUserId == null || candidateUserId.length() > 128
                || candidatePassword == null || candidatePassword.length() > 512) {
            return false;
        }
        return constantTime(userId, candidateUserId)
                && constantTime(password, candidatePassword);
    }

    private boolean constantTime(String expected, String actual) {
        byte[] left = expected == null ? new byte[0]
                : expected.getBytes(StandardCharsets.UTF_8);
        byte[] right = actual == null ? new byte[0]
                : actual.getBytes(StandardCharsets.UTF_8);
        return java.security.MessageDigest.isEqual(left, right);
    }

    private boolean validId(String value) {
        return value != null && ID.matcher(value).matches();
    }

    private boolean validCallback(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null && !uri.getHost().isBlank()
                    && uri.getUserInfo() == null && uri.getFragment() == null;
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    private boolean positiveBounded(Duration value, Duration maximum) {
        return value != null && !value.isNegative() && !value.isZero()
                && value.compareTo(maximum) <= 0;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCallbackUrl() {
        return callbackUrl;
    }

    public void setCallbackUrl(String callbackUrl) {
        this.callbackUrl = callbackUrl == null ? "" : callbackUrl.trim();
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId == null ? "" : userId.trim();
    }

    public String getStationId() {
        return stationId;
    }

    public void setStationId(String stationId) {
        this.stationId = stationId == null ? "" : stationId.trim();
    }

    public String getStationDisplayName() {
        return stationDisplayName;
    }

    public void setStationDisplayName(String stationDisplayName) {
        this.stationDisplayName = stationDisplayName == null
                ? "" : stationDisplayName.trim();
    }

    public void setPassword(String password) {
        this.password = password == null ? "" : password;
    }

    public Set<BffRole> getRoles() {
        return roles;
    }

    public void setRoles(Set<BffRole> roles) {
        this.roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    public Set<String> getIncidentScopes() {
        return incidentScopes;
    }

    public void setIncidentScopes(Set<String> incidentScopes) {
        this.incidentScopes = incidentScopes == null ? Set.of()
                : incidentScopes.stream().map(String::trim).collect(
                java.util.stream.Collectors.toUnmodifiableSet());
    }

    public Duration getCsrfMaxAge() {
        return csrfMaxAge;
    }

    public void setCsrfMaxAge(Duration csrfMaxAge) {
        this.csrfMaxAge = csrfMaxAge;
    }

    public Duration getLockDuration() {
        return lockDuration;
    }

    public void setLockDuration(Duration lockDuration) {
        this.lockDuration = lockDuration;
    }

    public int getMaxFailedAttempts() {
        return maxFailedAttempts;
    }

    public void setMaxFailedAttempts(int maxFailedAttempts) {
        this.maxFailedAttempts = maxFailedAttempts;
    }
}
