package com.caseaxis.auth;

import com.caseaxis.cases.CreateCaseNoteRequest;
import com.caseaxis.cases.CreateCaseRequest;
import com.caseaxis.cases.CreateCaseTaskRequest;
import com.caseaxis.cases.TransitionStatusRequest;
import com.caseaxis.cases.UpdateCasePriorityRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DemoAccessTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    private UUID demoId;
    private UUID orgId;
    private String demoToken;

    @BeforeEach
    void setUp() throws Exception {
        demoId = jdbcTemplate.queryForObject(
            "SELECT id FROM users WHERE username = 'demo' AND is_deleted = false",
            UUID.class
        );
        orgId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO organizations (id, name, created_by) VALUES (?, ?, ?)",
            orgId, "Demo Access Test Org", demoId
        );
        demoToken = login("demo", "demo123");
    }

    @Test
    void demoUser_canExercisePublicDemoWorkflowWithoutAdminRole() throws Exception {
        Integer adminRoleCount = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM user_roles ur
            JOIN roles r ON r.id = ur.role_id
            WHERE ur.user_id = ?
              AND ur.removed_at IS NULL
              AND r.code = 'ADMIN'
            """, Integer.class, demoId);
        org.assertj.core.api.Assertions.assertThat(adminRoleCount).isZero();

        CreateCaseRequest createCaseRequest = new CreateCaseRequest(
            "Demo workflow validation case",
            "Created by automated demo access validation.",
            "MEDIUM",
            "GENERAL",
            orgId,
            null,
            null
        );
        String createCaseResponse = mockMvc.perform(post("/api/cases")
                .header("Authorization", "Bearer " + demoToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createCaseRequest)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        String caseId = objectMapper.readTree(createCaseResponse).at("/data/id").asText();

        CreateCaseNoteRequest noteRequest = new CreateCaseNoteRequest(
            "Demo validation note.",
            false,
            null
        );
        mockMvc.perform(post("/api/cases/" + caseId + "/notes")
                .header("Authorization", "Bearer " + demoToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(noteRequest)))
            .andExpect(status().isCreated());

        CreateCaseTaskRequest taskRequest = new CreateCaseTaskRequest(
            "Demo validation task",
            "Created by automated demo access validation.",
            "PENDING",
            null,
            LocalDate.now().plusDays(7)
        );
        mockMvc.perform(post("/api/cases/" + caseId + "/tasks")
                .header("Authorization", "Bearer " + demoToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(taskRequest)))
            .andExpect(status().isCreated());

        TransitionStatusRequest statusRequest = new TransitionStatusRequest(
            "ASSIGNED",
            "Demo validation status change."
        );
        mockMvc.perform(post("/api/cases/" + caseId + "/status")
                .header("Authorization", "Bearer " + demoToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(statusRequest)))
            .andExpect(status().isOk());

        UpdateCasePriorityRequest priorityRequest = new UpdateCasePriorityRequest(
            "HIGH",
            "Demo validation priority change."
        );
        mockMvc.perform(post("/api/cases/" + caseId + "/priority")
                .header("Authorization", "Bearer " + demoToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(priorityRequest)))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/cases/" + caseId + "/audit")
                .header("Authorization", "Bearer " + demoToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()", greaterThanOrEqualTo(5)));
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
