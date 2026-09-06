package com.c2guard.integration.speech;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SpeechApiPropertiesTest {

    @Test
    void enforcesHardRequestAndResponseBounds() {
        SpeechApiProperties properties = new SpeechApiProperties();

        assertThrows(IllegalArgumentException.class,
                () -> properties.setMaxAudioBytes(0));
        assertThrows(IllegalArgumentException.class,
                () -> properties.setMaxAudioBytes(SpeechApiProperties.HARD_MAX_AUDIO_BYTES + 1));
        assertThrows(IllegalArgumentException.class,
                () -> properties.setMaxResponseBytes(0));
        assertThrows(IllegalArgumentException.class,
                () -> properties.setMaxResponseBytes(
                        SpeechApiProperties.HARD_MAX_RESPONSE_BYTES + 1));
    }

    @Test
    void derivesNormalizedIamAudienceFromBaseUrlUnlessExplicitlySet() {
        SpeechApiProperties properties = new SpeechApiProperties();
        properties.setBaseUrl(URI.create("https://speech.example.test/"));
        assertEquals("https://speech.example.test", properties.getIamAudience());

        properties.setIamAudience("https://audience.example.test///");
        assertEquals("https://audience.example.test", properties.getIamAudience());
    }
}
