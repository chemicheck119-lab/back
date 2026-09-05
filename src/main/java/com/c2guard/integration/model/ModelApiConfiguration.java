package com.c2guard.integration.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.http.HttpClient;

@Configuration
@EnableConfigurationProperties(ModelApiProperties.class)
public class ModelApiConfiguration {

    @Bean
    RestClient modelApiRestClient(ModelApiProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getResponseTimeout());

        return RestClient.builder()
                .baseUrl(properties.getBaseUrl().toString())
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    ModelApiIdentityTokenProvider modelApiIdentityTokenProvider(ModelApiProperties properties)
            throws IOException {
        if (!properties.isIamAuthenticationEnabled()) {
            return ModelApiIdentityTokenProvider.disabled();
        }
        return new GoogleModelApiIdentityTokenProvider(properties.getIamAudience());
    }

    @Bean
    ModelApiClient modelApiClient(RestClient modelApiRestClient,
                                  ObjectMapper objectMapper,
                                  ModelApiProperties properties,
                                  MeterRegistry meterRegistry,
                                  ModelApiIdentityTokenProvider identityTokenProvider) {
        return new RestModelApiClient(modelApiRestClient, objectMapper, properties,
                Thread::sleep, new ModelApiTelemetry(meterRegistry), identityTokenProvider);
    }
}
