package com.caseaxis.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    @Test
    void constructorRejectsMissingSecret() {
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> new JwtService(null, 86400000)
        );

        assertTrue(exception.getMessage().contains("JWT_SECRET"));
    }

    @Test
    void constructorRejectsBlankSecret() {
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> new JwtService("   ", 86400000)
        );

        assertTrue(exception.getMessage().contains("JWT_SECRET"));
    }

    @Test
    void constructorRejectsSecretUnderThirtyTwoBytes() {
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> new JwtService("short-secret", 86400000)
        );

        assertTrue(exception.getMessage().contains("JWT_SECRET"));
    }

    @Test
    void constructorAcceptsSecretAtLeastThirtyTwoBytes() {
        assertDoesNotThrow(() -> new JwtService("caseaxis-test-jwt-secret-32-bytes-minimum", 86400000));
    }
}
