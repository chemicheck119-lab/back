package com.c2guard.integration.speech;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties(prefix = "chemicheck119.speech-api")
public class SpeechApiProperties {

    public static final int HARD_MAX_AUDIO_BYTES = 16 * 1024 * 1024;
    public static final int HARD_MAX_RESPONSE_BYTES = 2 * 1024 * 1024;

    private URI baseUrl = URI.create("http://localhost:8081");
    private String apiKey = "";
    private boolean iamAuthenticationEnabled = false;
    private String iamAudience = "";
    private String schema = "chemicheck119-speech-api-v1";
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration responseTimeout = Duration.ofSeconds(45);
    private int maxAudioBytes = HARD_MAX_AUDIO_BYTES;
    private int maxResponseBytes = HARD_MAX_RESPONSE_BYTES;

    public URI getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(URI baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    public boolean hasApiKey() {
        return !apiKey.isBlank();
    }

    public boolean isIamAuthenticationEnabled() {
        return iamAuthenticationEnabled;
    }

    public void setIamAuthenticationEnabled(boolean iamAuthenticationEnabled) {
        this.iamAuthenticationEnabled = iamAuthenticationEnabled;
    }

    public String getIamAudience() {
        if (iamAudience != null && !iamAudience.isBlank()) {
            return trimTrailingSlash(iamAudience.trim());
        }
        return trimTrailingSlash(baseUrl.toString());
    }

    public void setIamAudience(String iamAudience) {
        this.iamAudience = iamAudience == null ? "" : iamAudience;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
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

    public int getMaxAudioBytes() {
        return maxAudioBytes;
    }

    public void setMaxAudioBytes(int maxAudioBytes) {
        if (maxAudioBytes <= 0 || maxAudioBytes > HARD_MAX_AUDIO_BYTES) {
            throw new IllegalArgumentException("speech max audio bytes is outside the hard limit");
        }
        this.maxAudioBytes = maxAudioBytes;
    }

    public int getMaxResponseBytes() {
        return maxResponseBytes;
    }

    public void setMaxResponseBytes(int maxResponseBytes) {
        if (maxResponseBytes <= 0 || maxResponseBytes > HARD_MAX_RESPONSE_BYTES) {
            throw new IllegalArgumentException("speech max response bytes is outside the hard limit");
        }
        this.maxResponseBytes = maxResponseBytes;
    }

    private static String trimTrailingSlash(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }
}
