package com.caseaxis.audit;

import com.caseaxis.auth.LoginRequest;
import com.caseaxis.cases.CreateCaseRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EnterpriseControlsTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PasswordEncoder passwordEncoder;

    @Value("${ADMIN_INITIAL_PASSWORD:admin}")
    private String adminPassword;

    private UUID adminId;
    private UUID orgId;
    private String adminToken;
    private String auditorToken;

    @BeforeEach
    void setUp() throws Exception {
        adminId = jdbcTemplate.queryForObject(
            "SELECT id FROM users WHERE username = 'admin' AND is_deleted = false",
            UUID.class
        );
        jdbcTemplate.update(
            "UPDATE users SET password_hash = ? WHERE username = ? AND is_deleted = false",
            passwordEncoder.encode(adminPassword), "admin"
        );

        String auditorUsername = "auditor_" + UUID.randomUUID();
        String auditorPassword = "auditor-password";
        UUID auditorId = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO users (id, username, email, password_hash, first_name, last_name,
                               is_active, is_deleted, created_by)
            VALUES (?, ?, ?, ?, ?, ?, true, false, ?)
            """,
            auditorId,
            auditorUsername,
            auditorUsername + "@caseaxis.local",
            passwordEncoder.encode(auditorPassword),
            "Audit",
            "User",
            adminId
        );
        UUID auditorRoleId = jdbcTemplate.queryForObject(
            "SELECT id FROM roles WHERE code = 'AUDITOR' AND is_active = true",
            UUID.class
        );
        jdbcTemplate.update("""
            INSERT INTO user_roles (id, user_id, role_id, assigned_by)
            VALUES (?, ?, ?, ?)
            """, UUID.randomUUID(), auditorId, auditorRoleId, adminId);

        orgId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO organizations (id, name, created_by) VALUES (?, ?, ?)",
            orgId, "Enterprise Controls Test Org", adminId
        );

        adminToken = login("admin", adminPassword);
        auditorToken = login(auditorUsername, auditorPassword);
    }

    @Test
    void auditor_canReadButCannotMutateCases() throws Exception {
        mockMvc.perform(get("/api/cases")
                .header("Authorization", "Bearer " + auditorToken))
            .andExpect(status().isOk());

        CreateCaseRequest req = new CreateCaseRequest(
            "Auditor forbidden create", null, "LOW", "GENERAL", orgId, null, null);
        mockMvc.perform(post("/api/cases")
                .header("Authorization", "Bearer " + auditorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void mutatingCaseActions_createReadableAuditEvents() throws Exception {
        String caseId = createCase("Enterprise audit create");

        Long createdEvents = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM audit_logs
            WHERE entity_type = 'case'
              AND entity_id = ?::uuid
              AND action = 'case_created'
            """, Long.class, caseId);
        org.assertj.core.api.Assertions.assertThat(createdEvents).isEqualTo(1);

        mockMvc.perform(post("/api/cases/" + caseId + "/status")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetStatusCode\":\"ASSIGNED\",\"reason\":\"Queue assignment\"}"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/cases/" + caseId + "/audit")
                .header("Authorization", "Bearer " + auditorToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()", greaterThanOrEqualTo(2)))
            .andExpect(jsonPath("$.data[0].eventType").value("Status changed"))
            .andExpect(jsonPath("$.data[0].summary", containsString("Changed status from NEW to ASSIGNED")))
            .andExpect(jsonPath("$.data[0].oldValues").doesNotExist())
            .andExpect(jsonPath("$.data[0].newValues").doesNotExist())
            .andExpect(jsonPath("$.data[0].metadata").doesNotExist());
    }

    private String createCase(String title) throws Exception {
        CreateCaseRequest req = new CreateCaseRequest(
            title, null, "HIGH", "COMPLAINT", orgId, null, null);
        String resp = mockMvc.perform(post("/api/cases")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).at("/data/id").asText();
    }

    private String login(String username, String password) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(username, password))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).at("/data/token").asText();
    }
}
