package com.c2guard.bff.movement;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MovementProperties.class)
class MovementConfiguration {

    @Bean
    @ConditionalOnMissingBean(RouteProvider.class)
    RouteProvider unavailableRouteProvider() {
        return request -> new RouteProvider.UnavailableRoute(
                "지도 사업자가 아직 구성되지 않아 실제 도로 경로를 제공할 수 없습니다.",
                false);
    }
}
