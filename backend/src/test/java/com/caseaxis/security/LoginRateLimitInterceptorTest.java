package com.caseaxis.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginRateLimitInterceptorTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-06-19T12:00:00Z"));
    private final LoginRateLimitInterceptor interceptor = new LoginRateLimitInterceptor(clock);
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @Test
    void allowsTenLoginAttemptsPerMinuteThenRejects() {
        for (int i = 0; i < 10; i++) {
            assertDoesNotThrow(() -> interceptor.preHandle(loginRequest("203.0.113.10"), response, new Object()));
        }

        assertThrows(
            LoginRateLimitExceededException.class,
            () -> interceptor.preHandle(loginRequest("203.0.113.10"), response, new Object())
        );
    }

    @Test
    void readsFirstForwardedIpAndFallsBackToRemoteAddress() {
        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest request = loginRequest("10.0.0.5");
            request.addHeader("X-Forwarded-For", "198.51.100.20, 10.0.0.1");
            assertDoesNotThrow(() -> interceptor.preHandle(request, response, new Object()));
        }

        assertThrows(
            LoginRateLimitExceededException.class,
            () -> {
                MockHttpServletRequest request = loginRequest("10.0.0.5");
                request.addHeader("X-Forwarded-For", "198.51.100.20, 10.0.0.1");
                interceptor.preHandle(request, response, new Object());
            }
        );

        assertDoesNotThrow(() -> interceptor.preHandle(loginRequest("10.0.0.5"), response, new Object()));
    }

    @Test
    void allowsAgainAfterWindowExpires() {
        for (int i = 0; i < 10; i++) {
            assertDoesNotThrow(() -> interceptor.preHandle(loginRequest("203.0.113.30"), response, new Object()));
        }

        clock.advanceSeconds(61);

        assertDoesNotThrow(() -> interceptor.preHandle(loginRequest("203.0.113.30"), response, new Object()));
    }

    @Test
    void ignoresNonPostRequests() {
        for (int i = 0; i < 20; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/login");
            request.setRemoteAddr("203.0.113.40");
            assertDoesNotThrow(() -> interceptor.preHandle(request, response, new Object()));
        }

        assertDoesNotThrow(() -> interceptor.preHandle(loginRequest("203.0.113.40"), response, new Object()));
    }

    @Test
    void canBeDisabledByConfigurationForSpringContextTests() {
        LoginRateLimitInterceptor disabledInterceptor =
            new LoginRateLimitInterceptor(clock, false, 10, Duration.ofMinutes(1));

        for (int i = 0; i < 25; i++) {
            assertDoesNotThrow(() -> disabledInterceptor.preHandle(loginRequest("203.0.113.50"), response, new Object()));
        }
    }

    @Test
    void honorsConfiguredAttemptLimitAndWindow() {
        LoginRateLimitInterceptor configuredInterceptor =
            new LoginRateLimitInterceptor(clock, true, 2, Duration.ofSeconds(5));

        assertDoesNotThrow(() -> configuredInterceptor.preHandle(loginRequest("203.0.113.60"), response, new Object()));
        assertDoesNotThrow(() -> configuredInterceptor.preHandle(loginRequest("203.0.113.60"), response, new Object()));
        assertThrows(
            LoginRateLimitExceededException.class,
            () -> configuredInterceptor.preHandle(loginRequest("203.0.113.60"), response, new Object())
        );

        clock.advanceSeconds(6);

        assertDoesNotThrow(() -> configuredInterceptor.preHandle(loginRequest("203.0.113.60"), response, new Object()));
    }

    @Test
    void evictsExpiredEntriesOnceTrackedClientMapExceedsBound() {
        for (int i = 0; i < LoginRateLimitInterceptor.MAX_TRACKED_CLIENTS; i++) {
            String ip = "10." + (i / 65536 % 256) + "." + (i / 256 % 256) + "." + (i % 256);
            assertDoesNotThrow(() -> interceptor.preHandle(loginRequest(ip), response, new Object()));
        }

        clock.advanceSeconds(61);

        assertDoesNotThrow(() -> interceptor.preHandle(loginRequest("203.0.113.99"), response, new Object()));

        assertTrue(interceptor.trackedClientCount() <= LoginRateLimitInterceptor.MAX_TRACKED_CLIENTS);
    }

    private MockHttpServletRequest loginRequest(String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr(remoteAddress);
        return request;
    }

    private static class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
