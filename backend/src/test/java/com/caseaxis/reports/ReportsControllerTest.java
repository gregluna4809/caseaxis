package com.caseaxis.reports;

import com.caseaxis.auth.LoginRequest;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReportsControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private UserRepository userRepository;

    private UUID orgId;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        UUID adminId = userRepository.findByUsernameAndDeletedFalse("admin").orElseThrow().getId();

        orgId = UuidGenerator.generate();
        jdbcTemplate.update(
            "INSERT INTO organizations (id, name, created_by) VALUES (?, ?, ?)",
            orgId, "Reports Test Org " + orgId, adminId
        );

        String loginBody = objectMapper.writeValueAsString(new LoginRequest("admin", "admin"));
        String resp = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody))
            .andReturn().getResponse().getContentAsString();
        token = objectMapper.readTree(resp).at("/data/token").asText();
    }

    @Test
    void reportEndpoints_authenticated_returnAggregateData() throws Exception {
        createCase("Reports Aggregate Complaint", "HIGH", "COMPLAINT", LocalDate.now().minusDays(1));
        String params = "?startDate=" + LocalDate.now().minusDays(30) + "&endDate=" + LocalDate.now();

        mockMvc.perform(get("/api/reports/summary" + params).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.totalCases").isNumber())
            .andExpect(jsonPath("$.data.openTasks").isNumber());

        mockMvc.perform(get("/api/reports/status-distribution" + params).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray());

        mockMvc.perform(get("/api/reports/type-distribution" + params).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray());

        mockMvc.perform(get("/api/reports/overdue-aging" + params).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray());

        mockMvc.perform(get("/api/reports/assignee-workload" + params).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray());

        mockMvc.perform(get("/api/reports/organization-workload" + params).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray());

        mockMvc.perform(get("/api/reports/closure-trend" + params).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void exports_authenticated_returnServerGeneratedPayloads() throws Exception {
        mockMvc.perform(get("/api/reports/export/json")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.summary.totalCases").isNumber())
            .andExpect(jsonPath("$.data.closureTrend").isArray());

        mockMvc.perform(get("/api/reports/export/csv")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("text/csv"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("summary,totalCases")));
    }

    @Test
    void reports_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/reports/summary"))
            .andExpect(status().isUnauthorized());
    }

    private void createCase(String title, String priorityCode, String typeCode, LocalDate dueDate) throws Exception {
        var req = new CreateCaseRequest(title, null, priorityCode, typeCode, orgId, null, dueDate);
        mockMvc.perform(post("/api/cases")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated());
    }
}
