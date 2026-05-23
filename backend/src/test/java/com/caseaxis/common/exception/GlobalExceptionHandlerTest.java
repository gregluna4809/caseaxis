package com.caseaxis.common.exception;

import com.caseaxis.auth.LoginRequest;
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
    @Autowired private UserRepository userRepository;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        String loginBody = objectMapper.writeValueAsString(new LoginRequest("admin", "admin"));
        String resp = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody))
            .andReturn().getResponse().getContentAsString();
        token = objectMapper.readTree(resp).at("/data/token").asText();
    }

    @Test
    void unknownRoute_authenticated_returns404NotFound() throws Exception {
        mockMvc.perform(get("/api/this-route-does-not-exist")
                .header("Authorization", "Bearer " + token))
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
                .header("Authorization", "Bearer " + token))
            .andReturn().getResponse().getStatus();

        org.assertj.core.api.Assertions.assertThat(status).isNotEqualTo(500);
    }
}
