package com.c2guard.security;

import com.c2guard.bff.common.BffRequestIdFilter;
import com.c2guard.bff.intake.IncidentReplayProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Clock;
import java.util.List;

@Configuration
@EnableConfigurationProperties(BffSecurityProperties.class)
public class BffSecurityConfiguration {

    @Bean
    Clock bffSecurityClock() {
        return Clock.systemUTC();
    }

    @Bean
    SecurityFilterChain bffSecurityFilterChain(
            HttpSecurity http,
            BffSessionAuthenticationFilter sessionAuthenticationFilter,
            BffSecurityErrorHandler securityErrorHandler,
            IncidentPathAuthorizationManager incidentAuthorization,
            CorsConfigurationSource corsConfigurationSource,
            BffSecurityProperties properties,
            IncidentReplayProperties replayProperties) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(securityErrorHandler)
                        .accessDeniedHandler(securityErrorHandler))
                .authorizeHttpRequests(authorize -> {
                    authorize
                            .requestMatchers(CorsUtils::isPreFlightRequest).permitAll()
                            .requestMatchers("/actuator/health/**").permitAll();
                    if (properties.isPublicAnalysisEnabled()) {
                        authorize.requestMatchers(HttpMethod.POST,
                                "/api/c2guard/v1/incidents/analyze",
                                "/api/c2guard/v1/substances/discover").permitAll();
                    } else {
                        authorize.requestMatchers(HttpMethod.POST,
                                "/api/c2guard/v1/incidents/analyze",
                                "/api/c2guard/v1/substances/discover").authenticated();
                    }
                    if (replayProperties.isEnabled()
                            && replayProperties.isPublicEndpointEnabled()) {
                        authorize.requestMatchers(HttpMethod.GET,
                                "/api/c2guard/v1/intake/replay-stream/*").permitAll();
                    }
                    authorize
                            .requestMatchers("/api/c2guard/v1/incidents/*/**")
                            .access(incidentAuthorization)
                            .requestMatchers("/api/**").authenticated()
                            .anyRequest().permitAll();
                })
                .addFilterBefore(sessionAuthenticationFilter,
                        AnonymousAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(BffSecurityProperties properties) {
        CorsConfiguration configuration = buildCorsConfiguration(properties);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    private CorsConfiguration buildCorsConfiguration(BffSecurityProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.getAllowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(HttpHeaders.CONTENT_TYPE,
                BffRequestIdFilter.HEADER, "X-CSRF-Token"));
        configuration.setExposedHeaders(List.of(BffRequestIdFilter.HEADER));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        return configuration;
    }
}
