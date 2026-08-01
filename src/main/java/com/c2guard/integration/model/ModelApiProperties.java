package com.c2guard.integration.model;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties(prefix = "chemicheck119.model-api")
public class ModelApiProperties {

    private URI baseUrl = URI.create("http://localhost:8000");
    private String apiKey = "";
    private String schema = "chemiguard119-api-v1";
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration responseTimeout = Duration.ofSeconds(15);
    private int maxRetries = 1;

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
        this.apiKey = apiKey == null ? "" : apiKey;
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

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = Math.max(0, Math.min(maxRetries, 1));
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
