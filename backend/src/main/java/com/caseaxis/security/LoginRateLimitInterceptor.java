package com.caseaxis.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class LoginRateLimitInterceptor implements HandlerInterceptor {

    static final int MAX_TRACKED_CLIENTS = 10_000;

    private final Clock clock;
    private final boolean enabled;
    private final int maxAttempts;
    private final Duration window;
    private final ConcurrentMap<String, Deque<Instant>> attemptsByClientIp = new ConcurrentHashMap<>();

    @Autowired
    public LoginRateLimitInterceptor(
        @Value("${application.security.login-rate-limit.enabled:true}") boolean enabled,
        @Value("${application.security.login-rate-limit.max-attempts:10}") int maxAttempts,
        @Value("${application.security.login-rate-limit.window-seconds:60}") long windowSeconds
    ) {
        this(Clock.systemUTC(), enabled, maxAttempts, Duration.ofSeconds(windowSeconds));
    }

    LoginRateLimitInterceptor(Clock clock) {
        this(clock, true, 10, Duration.ofMinutes(1));
    }

    LoginRateLimitInterceptor(Clock clock, boolean enabled, int maxAttempts, Duration window) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("login rate-limit max-attempts must be at least 1");
        }
        if (window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("login rate-limit window must be positive");
        }
        this.clock = clock;
        this.enabled = enabled;
        this.maxAttempts = maxAttempts;
        this.window = window;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!enabled) {
            return true;
        }
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String clientIp = clientIp(request);
        Instant now = Instant.now(clock);
        Instant windowStart = now.minus(window);
        AtomicBoolean allowed = new AtomicBoolean(true);

        // Single-instance limiter: protects one running backend process only.
        attemptsByClientIp.compute(clientIp, (ip, attempts) -> {
            Deque<Instant> currentAttempts = attempts == null ? new ArrayDeque<>() : attempts;
            removeExpired(currentAttempts, windowStart);
            if (currentAttempts.size() >= maxAttempts) {
                allowed.set(false);
                return currentAttempts;
            }
            currentAttempts.addLast(now);
            return currentAttempts;
        });

        if (attemptsByClientIp.size() > MAX_TRACKED_CLIENTS) {
            evictStaleEntries(windowStart);
        }

        if (!allowed.get()) {
            throw new LoginRateLimitExceededException("Too many login attempts. Try again later.");
        }
        return true;
    }

    private static void removeExpired(Deque<Instant> attempts, Instant windowStart) {
        while (!attempts.isEmpty() && attempts.peekFirst().isBefore(windowStart)) {
            attempts.removeFirst();
        }
    }

    private void evictStaleEntries(Instant windowStart) {
        attemptsByClientIp.forEach((ip, attempts) ->
            attemptsByClientIp.computeIfPresent(ip, (key, currentAttempts) -> {
                removeExpired(currentAttempts, windowStart);
                return currentAttempts.isEmpty() ? null : currentAttempts;
            })
        );
    }

    int trackedClientCount() {
        return attemptsByClientIp.size();
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",", 2)[0].trim();
        }
        return request.getRemoteAddr();
    }
}
