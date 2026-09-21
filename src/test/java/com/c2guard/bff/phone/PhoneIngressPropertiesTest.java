package com.c2guard.bff.phone;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneIngressPropertiesTest {

    @ParameterizedTest
    @ValueSource(strings = {"test-token", "test-token\n", "test-token\r\n", " \ttest-token\r\n "})
    void normalizesConfiguredTokenLikeGateway(String configured) {
        PhoneIngressProperties properties = new PhoneIngressProperties();
        properties.setToken(configured);
        assertThat(properties.getToken()).isEqualTo("test-token");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\r\n", " \t\r\n "})
    void absentTokenRemainsEmptyAndCannotAuthorize(String configured) {
        PhoneIngressProperties properties = new PhoneIngressProperties();
        properties.setToken(configured);
        assertThat(properties.getToken()).isEmpty();
    }

    @Test
    void doesNotSilentlyRemoveEmbeddedWhitespace() {
        PhoneIngressProperties properties = new PhoneIngressProperties();
        properties.setToken("token with space\n");
        assertThat(properties.getToken()).isEqualTo("token with space");
    }
}
