package com.c2guard.integration.speech;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.http.HttpClient;

@Configuration
@EnableConfigurationProperties(SpeechApiProperties.class)
public class SpeechApiConfiguration {

    @Bean
    RestClient speechApiRestClient(SpeechApiProperties properties) {
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
    SpeechApiIdentityTokenProvider speechApiIdentityTokenProvider(
            SpeechApiProperties properties) throws IOException {
        if (!properties.isIamAuthenticationEnabled()) {
            return SpeechApiIdentityTokenProvider.disabled();
        }
        return new GoogleSpeechApiIdentityTokenProvider(properties.getIamAudience());
    }

    @Bean
    SpeechApiClient speechApiClient(
                                    @Qualifier("speechApiRestClient") RestClient speechApiRestClient,
                                    ObjectMapper objectMapper,
                                    SpeechApiProperties properties,
                                    MeterRegistry meterRegistry,
                                    SpeechApiIdentityTokenProvider identityTokenProvider) {
        return new RestSpeechApiClient(speechApiRestClient, objectMapper, properties,
                meterRegistry, identityTokenProvider);
    }
}
