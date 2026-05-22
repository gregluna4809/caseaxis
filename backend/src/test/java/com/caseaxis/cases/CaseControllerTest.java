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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the case workflow endpoints.
 *
 * @Transactional causes Spring to roll back all DB changes after each test,
 * which also handles case_status_history rows (immutable trigger only fires for
 * explicit DML, not ROLLBACK).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CaseControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
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
            orgId, "Test Org " + orgId, adminId
        );

        String loginBody = objectMapper.writeValueAsString(new LoginRequest("admin", "admin"));
        String resp = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody))
            .andReturn().getResponse().getContentAsString();
        token = objectMapper.readTree(resp).at("/data/token").asText();
    }

    @Test
    void listCases_authenticated_returnsPage() throws Exception {
        mockMvc.perform(get("/api/cases")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    void listCases_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/cases"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void createCase_validRequest_returnsCreated() throws Exception {
        var req = new CreateCaseRequest(
            "Phase 6 Integration Test", "Validates createCase endpoint",
            "HIGH", "COMPLAINT", orgId, null, null
        );
        mockMvc.perform(post("/api/cases")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.caseNumber").value(matchesPattern("CA-\\d{6}")))
            .andExpect(jsonPath("$.data.statusCode").value("NEW"))
            .andExpect(jsonPath("$.data.priorityCode").value("HIGH"))
            .andExpect(jsonPath("$.data.typeCode").value("COMPLAINT"))
            .andExpect(jsonPath("$.data.createdAt").isNotEmpty());
    }

    @Test
    void createCase_missingSubject_returns400() throws Exception {
        var req = new CreateCaseRequest("No subject", null, "LOW", "GENERAL", null, null, null);
        mockMvc.perform(post("/api/cases")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void getCaseById_existingCase_returnsDetail() throws Exception {
        String caseId = createTestCase("Detail Lookup Test", "MEDIUM", "INQUIRY");

        mockMvc.perform(get("/api/cases/" + caseId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(caseId))
            .andExpect(jsonPath("$.data.statusCode").value("NEW"))
            .andExpect(jsonPath("$.data.createdAt").isNotEmpty())
            .andExpect(jsonPath("$.data.updatedAt").isNotEmpty());
    }

    @Test
    void getCaseById_nonexistent_returns404() throws Exception {
        mockMvc.perform(get("/api/cases/" + UUID.randomUUID())
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound());
    }

    @Test
    void assignCase_validAssignee_updatesAssignedToId() throws Exception {
        String caseId = createTestCase("Assignment Test", "LOW", "GENERAL");

        var assignReq = new AssignCaseRequest(adminId, "Initial assignment");
        mockMvc.perform(post("/api/cases/" + caseId + "/assign")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(assignReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.assignedToId").value(adminId.toString()))
            .andExpect(jsonPath("$.data.assignedAt").isNotEmpty());
    }

    @Test
    void assignCase_reassign_exactlyOneActiveAssignment() throws Exception {
        String caseId = createTestCase("Reassignment Test", "MEDIUM", "APPLICATION");

        var first = new AssignCaseRequest(adminId, "First assignment");
        mockMvc.perform(post("/api/cases/" + caseId + "/assign")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(first)))
            .andExpect(status().isOk());

        // Second assignment succeeds — DB constraint uq_case_assignments_one_active
        // would reject the insert with 500 if the prior row wasn't closed first.
        var second = new AssignCaseRequest(adminId, "Reassigned");
        mockMvc.perform(post("/api/cases/" + caseId + "/assign")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(second)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.assignedToId").value(adminId.toString()))
            .andExpect(jsonPath("$.data.assignedAt").isNotEmpty());

        // Confirm the GET reflects the current assignment
        mockMvc.perform(get("/api/cases/" + caseId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.assignedToId").value(adminId.toString()));
    }

    @Test
    void transitionStatus_newToAssigned_returnsUpdatedStatus() throws Exception {
        String caseId = createTestCase("Status Transition Test", "HIGH", "INVESTIGATION");

        var req = new TransitionStatusRequest("ASSIGNED", "Moving to worker queue");
        mockMvc.perform(post("/api/cases/" + caseId + "/status")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.statusCode").value("ASSIGNED"));
    }

    @Test
    void transitionStatus_invalidTransition_returns409() throws Exception {
        // NEW → APPROVED is not in the allowed transitions matrix
        String caseId = createTestCase("Invalid Transition Test", "LOW", "GENERAL");

        var req = new TransitionStatusRequest("APPROVED", null);
        mockMvc.perform(post("/api/cases/" + caseId + "/status")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void transitionStatus_terminalToReopened_incrementsReopenedCount() throws Exception {
        String caseId = createTestCase("Reopen Test", "HIGH", "COMPLAINT");

        transition(caseId, "ASSIGNED");
        transition(caseId, "IN_REVIEW");
        transition(caseId, "CLOSED");

        var req = new TransitionStatusRequest("REOPENED", "Client requested reopen");
        mockMvc.perform(post("/api/cases/" + caseId + "/status")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.statusCode").value("REOPENED"))
            .andExpect(jsonPath("$.data.reopenedCount").value(1));
    }

    // --- Test helpers ---

    private String createTestCase(String title, String priorityCode, String typeCode) throws Exception {
        var req = new CreateCaseRequest(title, null, priorityCode, typeCode, orgId, null, null);
        String resp = mockMvc.perform(post("/api/cases")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).at("/data/id").asText();
    }

    private void transition(String caseId, String targetCode) throws Exception {
        var req = new TransitionStatusRequest(targetCode, null);
        mockMvc.perform(post("/api/cases/" + caseId + "/status")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk());
    }
}
