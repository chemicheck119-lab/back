package com.c2guard.bff.intake;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
@EnableConfigurationProperties(IncidentReplayProperties.class)
public class IncidentReplayConfiguration {

    @Bean(destroyMethod = "shutdown")
    ScheduledExecutorService incidentReplayExecutor() {
        return Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "incident-replay-stream");
            thread.setDaemon(true);
            return thread;
        });
    }
}
