package com.caseaxis.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Value("${ADMIN_INITIAL_PASSWORD:admin}")
    private String adminPassword;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @BeforeEach
    void syncAdminPassword() {
        jdbcTemplate.update(
            "UPDATE users SET password_hash = ? WHERE username = ? AND is_deleted = false",
            passwordEncoder.encode(adminPassword), "admin"
        );
    }

    @Test
    void login_validCredentials_returnsToken() throws Exception {
        String body = objectMapper.writeValueAsString(new LoginRequest("admin", adminPassword));
        String response = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.token").isNotEmpty())
            .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(response).at("/data/token").asText();
        org.assertj.core.api.Assertions.assertThat(token).isNotBlank();
    }

    @Test
    void login_demoCredentials_returnsTokenAndDoesNotAssignAdminRole() throws Exception {
        String body = objectMapper.writeValueAsString(new LoginRequest("demo", "demo123"));
        String response = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.token").isNotEmpty())
            .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(response).at("/data/token").asText();
        org.assertj.core.api.Assertions.assertThat(token).isNotBlank();

        Integer adminRoleCount = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM users u
            JOIN user_roles ur ON ur.user_id = u.id
            JOIN roles r ON r.id = ur.role_id
            WHERE u.username = 'demo'
              AND u.is_deleted = false
              AND ur.removed_at IS NULL
              AND r.code = 'ADMIN'
            """, Integer.class);

        Integer supervisorRoleCount = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM users u
            JOIN user_roles ur ON ur.user_id = u.id
            JOIN roles r ON r.id = ur.role_id
            WHERE u.username = 'demo'
              AND u.is_deleted = false
              AND ur.removed_at IS NULL
              AND r.code = 'SUPERVISOR'
            """, Integer.class);

        org.assertj.core.api.Assertions.assertThat(adminRoleCount).isZero();
        org.assertj.core.api.Assertions.assertThat(supervisorRoleCount).isEqualTo(1);
    }

    @Test
    void login_invalidCredentials_returnsUnauthorized() throws Exception {
        String body = objectMapper.writeValueAsString(new LoginRequest("admin", "wrongpassword"));
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withoutToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/cases"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withValidToken_returns404NotUnauthorized() throws Exception {
        String body = objectMapper.writeValueAsString(new LoginRequest("admin", adminPassword));
        String response = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(response).at("/data/token").asText();
        org.assertj.core.api.Assertions.assertThat(token).isNotBlank();

        mockMvc.perform(get("/api/cases")
                .header("Authorization", "Bearer " + token))
            .andExpect(result ->
                assertNotEquals(401, result.getResponse().getStatus(),
                    "Valid JWT should not be rejected with 401"));
    }
}
