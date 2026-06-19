package com.caseaxis.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "application.security.cors.allowed-origins[0]=http://localhost:5173",
    "application.security.cors.allowed-origins[1]=http://127.0.0.1:5173",
    "application.security.cors.allowed-origins[2]=http://localhost:5174",
    "application.security.cors.allowed-origins[3]=http://127.0.0.1:5174"
})
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
    void login_validCredentials_setsHttpOnlyCookieWithoutReturningToken() throws Exception {
        String body = objectMapper.writeValueAsString(new LoginRequest("admin", adminPassword));
        var result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.username").value("admin"))
            .andExpect(jsonPath("$.data.roles").isArray())
            .andExpect(jsonPath("$.data.roles[0]").value("ADMIN"))
            .andExpect(jsonPath("$.data.token").doesNotExist())
            .andReturn();

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).contains("token=", "HttpOnly", "Secure", "SameSite=Strict", "Path=/", "Max-Age=");
    }

    @Test
    void login_validCredentialsFromLocalhostOrigin_setsCorsHeadersAndHttpOnlyCookie() throws Exception {
        String body = objectMapper.writeValueAsString(new LoginRequest("admin", adminPassword));
        var result = mockMvc.perform(post("/api/auth/login")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
            .andExpect(jsonPath("$.success").value(true))
            .andReturn();

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains("token=", "HttpOnly", "Secure", "SameSite=Strict", "Path=/", "Max-Age=");
    }

    @Test
    void loginPreflight_fromConfiguredLocalhostOrigin_allowsCredentials() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                .header(HttpHeaders.ORIGIN, "http://127.0.0.1:5174")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://127.0.0.1:5174"))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    void login_fromUnconfiguredOrigin_returnsInvalidCorsRequest() throws Exception {
        String body = objectMapper.writeValueAsString(new LoginRequest("admin", adminPassword));
        var result = mockMvc.perform(post("/api/auth/login")
                .header(HttpHeaders.ORIGIN, "http://evil.example")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isForbidden())
            .andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("Invalid CORS request");
    }

    @Test
    void login_demoCredentials_setsCookieAndDoesNotAssignAdminRole() throws Exception {
        String body = objectMapper.writeValueAsString(new LoginRequest("demo", "demo123"));
        var result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.username").value("demo"))
            .andExpect(jsonPath("$.data.roles[0]").value("SUPERVISOR"))
            .andExpect(jsonPath("$.data.token").doesNotExist())
            .andReturn();

        assertThat(result.getResponse().getHeader("Set-Cookie")).contains("token=");

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
    void protectedEndpoint_withValidCookie_returnsNotUnauthorized() throws Exception {
        Cookie token = com.caseaxis.test.TestAuthCookies.loginCookie(mockMvc, objectMapper, "admin", adminPassword);

        mockMvc.perform(get("/api/cases")
                .cookie(token))
            .andExpect(result ->
                assertNotEquals(401, result.getResponse().getStatus(),
                    "Valid JWT should not be rejected with 401"));
    }

    @Test
    void loginThenReadThenLogout_usesCookieFlowAndClearsCookie() throws Exception {
        Cookie authCookie = com.caseaxis.test.TestAuthCookies.loginCookie(mockMvc, objectMapper, "admin", adminPassword);

        mockMvc.perform(get("/api/cases").cookie(authCookie))
            .andExpect(result ->
                assertNotEquals(401, result.getResponse().getStatus(),
                    "Valid JWT cookie should authenticate read requests"));

        var logoutResult = mockMvc.perform(post("/api/auth/logout").cookie(authCookie))
            .andExpect(status().isOk())
            .andReturn();

        String logoutSetCookie = logoutResult.getResponse().getHeader("Set-Cookie");
        assertThat(logoutSetCookie).contains("token=", "Max-Age=0", "HttpOnly", "Secure", "SameSite=Strict", "Path=/");
    }
}


