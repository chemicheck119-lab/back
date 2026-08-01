package com.c2guard.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@ConfigurationProperties(prefix = "chemicheck119.bff-security")
public class BffSecurityProperties {

    private static final Pattern COOKIE_NAME = Pattern.compile(
            "^[A-Za-z0-9!#$%&'*+.^_`|~-]{1,64}$");

    private String sessionSecret = "";
    private String issuer = "chemicheck119-session-gateway";
    private String audience = "chemicheck119-bff";
    private Duration sessionMaxAge = Duration.ofHours(8);
    private Duration clockSkew = Duration.ofSeconds(30);
    private String cookieName = "CHEMICHECK119_SESSION";
    private boolean cookieSecure = true;
    private String cookieSameSite = "Lax";
    private List<String> allowedOrigins = List.of();
    private boolean publicAnalysisEnabled;

    public void setSessionSecret(String sessionSecret) {
        this.sessionSecret = sessionSecret == null ? "" : sessionSecret;
    }

    public boolean hasValidSessionSecret() {
        return sessionSecret.getBytes(StandardCharsets.UTF_8).length >= 32;
    }

    byte[] signingKey() {
        byte[] source = sessionSecret.getBytes(StandardCharsets.UTF_8);
        return Arrays.copyOf(source, source.length);
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = requireText(issuer, "issuer");
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = requireText(audience, "audience");
    }

    public Duration getSessionMaxAge() {
        return sessionMaxAge;
    }

    public void setSessionMaxAge(Duration sessionMaxAge) {
        if (sessionMaxAge == null || sessionMaxAge.isNegative() || sessionMaxAge.isZero()
                || sessionMaxAge.compareTo(Duration.ofHours(24)) > 0) {
            throw new IllegalArgumentException("session-max-age는 0초 초과 24시간 이하여야 합니다.");
        }
        this.sessionMaxAge = sessionMaxAge;
    }

    public Duration getClockSkew() {
        return clockSkew;
    }

    public void setClockSkew(Duration clockSkew) {
        if (clockSkew == null || clockSkew.isNegative()
                || clockSkew.compareTo(Duration.ofMinutes(5)) > 0) {
            throw new IllegalArgumentException("clock-skew는 0초 이상 5분 이하여야 합니다.");
        }
        this.clockSkew = clockSkew;
    }

    public String getCookieName() {
        return cookieName;
    }

    public void setCookieName(String cookieName) {
        String normalized = requireText(cookieName, "cookie-name");
        if (!COOKIE_NAME.matcher(normalized).matches()) {
            throw new IllegalArgumentException("cookie-name 형식이 올바르지 않습니다.");
        }
        this.cookieName = normalized;
    }

    public boolean isCookieSecure() {
        return cookieSecure;
    }

    public void setCookieSecure(boolean cookieSecure) {
        this.cookieSecure = cookieSecure;
    }

    public String getCookieSameSite() {
        return cookieSameSite;
    }

    public void setCookieSameSite(String cookieSameSite) {
        String normalized = requireText(cookieSameSite, "cookie-same-site");
        if (!List.of("Lax", "Strict", "None").contains(normalized)) {
            throw new IllegalArgumentException("cookie-same-site는 Lax, Strict, None 중 하나여야 합니다.");
        }
        this.cookieSameSite = normalized;
    }

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public boolean hasSafeCookiePolicy() {
        return !"None".equals(cookieSameSite) || cookieSecure;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        List<String> normalized = allowedOrigins == null ? List.of() : allowedOrigins.stream()
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .distinct()
                .toList();
        if (normalized.contains("*")) {
            throw new IllegalArgumentException("credential CORS에는 wildcard origin을 사용할 수 없습니다.");
        }
        this.allowedOrigins = normalized;
    }

    public boolean isPublicAnalysisEnabled() {
        return publicAnalysisEnabled;
    }

    public void setPublicAnalysisEnabled(boolean publicAnalysisEnabled) {
        this.publicAnalysisEnabled = publicAnalysisEnabled;
    }

    private String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 설정은 비어 있을 수 없습니다.");
        }
        return value.trim();
    }
}
