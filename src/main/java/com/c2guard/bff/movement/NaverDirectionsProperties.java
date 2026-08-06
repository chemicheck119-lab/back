package com.c2guard.bff.movement;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

@Validated
@ConfigurationProperties("chemicheck119.movement.naver-directions")
public class NaverDirectionsProperties {

    private boolean enabled;

    @NotNull
    private URI baseUrl = URI.create("https://maps.apigw.ntruss.com");

    private String clientId = "";
    private String clientSecret = "";

    @NotNull
    private Duration connectTimeout = Duration.ofSeconds(2);

    @NotNull
    private Duration responseTimeout = Duration.ofSeconds(5);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public URI getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(URI baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId == null ? "" : clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret == null ? "" : clientSecret;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getResponseTimeout() {
        return responseTimeout;
    }

    public void setResponseTimeout(Duration responseTimeout) {
        this.responseTimeout = responseTimeout;
    }

    @AssertTrue(message = "enabled Naver Directions requires HTTPS and both credentials")
    public boolean isConfigurationValid() {
        if (!enabled) {
            return true;
        }
        return baseUrl != null && "https".equalsIgnoreCase(baseUrl.getScheme())
                && !clientId.isBlank() && !clientSecret.isBlank()
                && connectTimeout != null && !connectTimeout.isNegative()
                && !connectTimeout.isZero()
                && responseTimeout != null && !responseTimeout.isNegative()
                && !responseTimeout.isZero();
    }
}
