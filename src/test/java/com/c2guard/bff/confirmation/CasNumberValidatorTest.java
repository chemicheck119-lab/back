package com.c2guard.bff.confirmation;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CasNumberValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {"7681-52-9", "7647-01-0", "50-00-0", "67-56-1"})
    void acceptsValidCasRegistryNumbers(String casNumber) {
        assertTrue(CasNumberValidator.isValid(casNumber));
    }

    @ParameterizedTest
    @ValueSource(strings = {"7681-52-8", "76-81-52-9", "ABC", "50-00-1", ""})
    void rejectsInvalidFormatsAndCheckDigits(String casNumber) {
        assertFalse(CasNumberValidator.isValid(casNumber));
    }
}
