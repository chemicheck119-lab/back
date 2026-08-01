package com.c2guard.persistence;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

@Component("persistence")
public class PersistenceHealthIndicator implements HealthIndicator {

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final PersistenceProperties properties;

    public PersistenceHealthIndicator(DataSource dataSource, JdbcTemplate jdbcTemplate,
                                      PersistenceProperties properties) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    @Override
    public Health health() {
        try (Connection connection = dataSource.getConnection()) {
            String url = connection.getMetaData().getURL();
            if (properties.isRequireExternalDatabase()
                    && (url == null || !url.startsWith("jdbc:postgresql:"))) {
                return Health.down()
                        .withDetail("code", "EXTERNAL_DATABASE_REQUIRED")
                        .build();
            }
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            if (result == null || result != 1) {
                return Health.down()
                        .withDetail("code", "DATABASE_PROBE_FAILED")
                        .build();
            }
            return Health.up()
                    .withDetail("database", connection.getMetaData().getDatabaseProductName())
                    .withDetail("external", url != null && url.startsWith("jdbc:postgresql:"))
                    .build();
        } catch (Exception error) {
            return Health.down()
                    .withDetail("code", "DATABASE_UNAVAILABLE")
                    .build();
        }
    }
}
