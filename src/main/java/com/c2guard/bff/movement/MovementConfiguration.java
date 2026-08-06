package com.c2guard.bff.movement;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration
@EnableConfigurationProperties({MovementProperties.class, NaverDirectionsProperties.class})
class MovementConfiguration {

    @Bean
    @ConditionalOnProperty(name = "chemicheck119.movement.naver-directions.enabled",
            havingValue = "true")
    RouteProvider naverDirectionsRouteProvider(ObjectMapper objectMapper,
                                                NaverDirectionsProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getResponseTimeout());
        RestClient restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl().toString())
                .requestFactory(requestFactory)
                .build();
        return new NaverDirectionsRouteProvider(restClient, objectMapper, properties);
    }

    @Bean
    @ConditionalOnMissingBean(RouteProvider.class)
    RouteProvider unavailableRouteProvider() {
        return request -> new RouteProvider.UnavailableRoute(
                "지도 사업자가 아직 구성되지 않아 실제 도로 경로를 제공할 수 없습니다.",
                false);
    }
}
