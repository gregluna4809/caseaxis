package com.caseaxis.common.exception;

import com.caseaxis.users.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that GlobalExceptionHandler maps Spring MVC routing exceptions to the
 * correct HTTP status codes instead of leaking them as 500.
 *
 * Regression for Spring Boot 3.4 / Spring Framework 6.2 behaviour where
 * NoHandlerFoundException is always thrown for unmatched routes and was
 * previously caught by @ExceptionHandler(Exception.class) → 500.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GlobalExceptionHandlerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @org.springframework.beans.factory.annotation.Value("${ADMIN_INITIAL_PASSWORD:admin}")
    private String adminPassword;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    @Autowired private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    @Autowired private UserRepository userRepository;

    private jakarta.servlet.http.Cookie token;

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.update(
            "UPDATE users SET password_hash = ? WHERE username = ? AND is_deleted = false",
            passwordEncoder.encode(adminPassword), "admin"
        );

        token = com.caseaxis.test.TestAuthCookies.loginCookie(mockMvc, objectMapper, "admin", adminPassword);
    }

    @Test
    void unknownRoute_authenticated_returns404NotFound() throws Exception {
        mockMvc.perform(get("/api/this-route-does-not-exist")
                .cookie(token))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void unknownRoute_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/this-route-does-not-exist"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownRoute_authenticated_doesNotReturn500() throws Exception {
        // This is the regression test: before the fix, Spring Framework 6.2
        // NoHandlerFoundException was caught by @ExceptionHandler(Exception.class)
        // and returned 500. It must now return 404.
        int status = mockMvc.perform(get("/api/nonexistent-endpoint")
                .cookie(token))
            .andReturn().getResponse().getStatus();

        org.assertj.core.api.Assertions.assertThat(status).isNotEqualTo(500);
    }
}



