package com.c2guard.bff.demolog;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DemoIncidentLogProperties.class)
public class DemoIncidentLogConfiguration {
}
