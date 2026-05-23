package com.caseaxis.cases;

import com.caseaxis.auth.LoginRequest;
import com.caseaxis.common.util.UuidGenerator;
import com.caseaxis.users.UserRepository;
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

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CaseTaskControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private UserRepository userRepository;

    private UUID orgId;
    private UUID adminId;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        adminId = userRepository.findByUsernameAndDeletedFalse("admin").orElseThrow().getId();

        orgId = UuidGenerator.generate();
        jdbcTemplate.update(
            "INSERT INTO organizations (id, name, created_by) VALUES (?, ?, ?)",
            orgId, "Task Test Org " + orgId, adminId
        );

        String loginBody = objectMapper.writeValueAsString(new LoginRequest("admin", "admin"));
        String resp = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody))
            .andReturn().getResponse().getContentAsString();
        token = objectMapper.readTree(resp).at("/data/token").asText();
    }

    @Test
    void createTask_validRequest_returnsCreated() throws Exception {
        String caseId = createTestCase("Task Creation Test");

        var req = new CreateCaseTaskRequest("Review documents", "Check all attached docs", null, null, null);
        mockMvc.perform(post("/api/cases/" + caseId + "/tasks")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.title").value("Review documents"))
            .andExpect(jsonPath("$.data.statusCode").value("PENDING"))
            .andExpect(jsonPath("$.data.caseId").value(caseId))
            .andExpect(jsonPath("$.data.createdAt").isNotEmpty());
    }

    @Test
    void createTask_withExplicitStatus_usesProvidedStatus() throws Exception {
        String caseId = createTestCase("Task Status Test");

        var req = new CreateCaseTaskRequest("Already started task", null, "IN_PROGRESS", null, null);
        mockMvc.perform(post("/api/cases/" + caseId + "/tasks")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.statusCode").value("IN_PROGRESS"));
    }

    @Test
    void createTask_emptyTitle_returns400() throws Exception {
        String caseId = createTestCase("Validation Case");

        var req = new CreateCaseTaskRequest("", null, null, null, null);
        mockMvc.perform(post("/api/cases/" + caseId + "/tasks")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createTask_caseNotFound_returns404() throws Exception {
        var req = new CreateCaseTaskRequest("Orphan task", null, null, null, null);
        mockMvc.perform(post("/api/cases/" + UUID.randomUUID() + "/tasks")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isNotFound());
    }

    @Test
    void listTasks_returnsTasksForCase() throws Exception {
        String caseId = createTestCase("List Tasks Case");
        createTask(caseId, "Task A");
        createTask(caseId, "Task B");

        mockMvc.perform(get("/api/cases/" + caseId + "/tasks")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
    void getTask_existingTask_returnsDetail() throws Exception {
        String caseId = createTestCase("Get Task Case");
        String taskId = createTask(caseId, "Specific task");

        mockMvc.perform(get("/api/cases/" + caseId + "/tasks/" + taskId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(taskId))
            .andExpect(jsonPath("$.data.title").value("Specific task"))
            .andExpect(jsonPath("$.data.statusCode").value("PENDING"));
    }

    @Test
    void getTask_nonexistentTask_returns404() throws Exception {
        String caseId = createTestCase("Task 404 Case");

        mockMvc.perform(get("/api/cases/" + caseId + "/tasks/" + UUID.randomUUID())
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound());
    }

    @Test
    void updateTask_changeStatus_updatesStatusCode() throws Exception {
        String caseId = createTestCase("Update Task Case");
        String taskId = createTask(caseId, "Updatable task");

        var req = new UpdateCaseTaskRequest("Updatable task", "Updated desc", "IN_PROGRESS", null, null);
        mockMvc.perform(put("/api/cases/" + caseId + "/tasks/" + taskId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.statusCode").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.data.description").value("Updated desc"));
    }

    @Test
    void updateTask_completeTask_setsCompletedAt() throws Exception {
        String caseId = createTestCase("Complete Task Case");
        String taskId = createTask(caseId, "Task to complete");

        var req = new UpdateCaseTaskRequest("Task to complete", null, "COMPLETED", null, null);
        mockMvc.perform(put("/api/cases/" + caseId + "/tasks/" + taskId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.statusCode").value("COMPLETED"))
            .andExpect(jsonPath("$.data.completedAt").isNotEmpty())
            .andExpect(jsonPath("$.data.completedBy").isNotEmpty());
    }

    @Test
    void deleteTask_existingTask_returns200AndExcludesFromList() throws Exception {
        String caseId = createTestCase("Delete Task Case");
        String taskId = createTask(caseId, "Task to delete");

        mockMvc.perform(delete("/api/cases/" + caseId + "/tasks/" + taskId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/cases/" + caseId + "/tasks")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void deleteTask_nonexistentTask_returns404() throws Exception {
        String caseId = createTestCase("Delete 404 Task Case");

        mockMvc.perform(delete("/api/cases/" + caseId + "/tasks/" + UUID.randomUUID())
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound());
    }

    // --- Test helpers ---

    private String createTestCase(String title) throws Exception {
        var req = new CreateCaseRequest(title, null, "LOW", "GENERAL", orgId, null, null);
        String resp = mockMvc.perform(post("/api/cases")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).at("/data/id").asText();
    }

    private String createTask(String caseId, String title) throws Exception {
        var req = new CreateCaseTaskRequest(title, null, null, null, null);
        String resp = mockMvc.perform(post("/api/cases/" + caseId + "/tasks")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).at("/data/id").asText();
    }
}
