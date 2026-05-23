package com.caseaxis.dashboard;

import com.caseaxis.auth.LoginRequest;
import com.caseaxis.cases.AssignCaseRequest;
import com.caseaxis.cases.CreateCaseRequest;
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

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DashboardControllerTest {

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
            orgId, "Dashboard Metrics Org " + orgId, adminId
        );

        String loginBody = objectMapper.writeValueAsString(new LoginRequest("admin", "admin"));
        String resp = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody))
            .andReturn().getResponse().getContentAsString();
        token = objectMapper.readTree(resp).at("/data/token").asText();
    }

    @Test
    void getMetrics_authenticated_returnsDashboardCounts() throws Exception {
        String caseId = createCase("Dashboard Assigned Overdue", "HIGH", "COMPLAINT", LocalDate.now().minusDays(1));
        var assignReq = new AssignCaseRequest(adminId, "Dashboard metrics assignment");
        mockMvc.perform(post("/api/cases/" + caseId + "/assign")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(assignReq)))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/dashboard/metrics")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.totalCases").isNumber())
            .andExpect(jsonPath("$.data.openCases").isNumber())
            .andExpect(jsonPath("$.data.assignedToMe").isNumber())
            .andExpect(jsonPath("$.data.overdueCases").isNumber())
            .andExpect(jsonPath("$.data.escalatedCases").isNumber())
            .andExpect(jsonPath("$.data.closedToday").isNumber());
    }

    @Test
    void getMetrics_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/dashboard/metrics"))
            .andExpect(status().isUnauthorized());
    }

    private String createCase(String title, String priorityCode, String typeCode, LocalDate dueDate) throws Exception {
        var req = new CreateCaseRequest(title, null, priorityCode, typeCode, orgId, null, dueDate);
        String resp = mockMvc.perform(post("/api/cases")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).at("/data/id").asText();
    }
}
