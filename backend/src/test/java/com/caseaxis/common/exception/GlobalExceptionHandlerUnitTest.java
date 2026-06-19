package com.caseaxis.common.exception;

import com.caseaxis.security.LoginRateLimitExceededException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GlobalExceptionHandlerUnitTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsLoginRateLimitToEnvelopeWith429() {
        var response = handler.handleLoginRateLimitExceeded(
            new LoginRateLimitExceededException("Too many login attempts. Try again later.")
        );

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
        assertEquals("Too many login attempts. Try again later.", response.getBody().getMessage());
    }
}
