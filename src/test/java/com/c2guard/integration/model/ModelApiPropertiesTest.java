package com.c2guard.integration.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModelApiPropertiesTest {

    @Test
    void defaultsMatchTheModelApiOperationsContract() {
        ModelApiProperties properties = new ModelApiProperties();

        assertEquals("http://localhost:8000", properties.getBaseUrl().toString());
        assertEquals("chemiguard119-api-v1", properties.getSchema());
        assertEquals(Duration.ofSeconds(2), properties.getConnectTimeout());
        assertEquals(Duration.ofSeconds(15), properties.getResponseTimeout());
        assertEquals(1, properties.getMaxRetries());
    }

    @Test
    void automaticRetriesCannotBeConfiguredAboveOne() {
        ModelApiProperties properties = new ModelApiProperties();

        properties.setMaxRetries(10);

        assertEquals(1, properties.getMaxRetries());
    }
}
