package com.caseaxis.dashboard;

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

import static org.assertj.core.api.Assertions.assertThat;
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
    @org.springframework.beans.factory.annotation.Value("${ADMIN_INITIAL_PASSWORD:admin}")
    private String adminPassword;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    @Autowired private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    @Autowired private UserRepository userRepository;

    private UUID orgId;
    private UUID adminId;
    private jakarta.servlet.http.Cookie token;

    @BeforeEach
    void setUp() throws Exception {
        adminId = userRepository.findByUsernameAndDeletedFalse("admin").orElseThrow().getId();
        jdbcTemplate.update(
            "UPDATE users SET password_hash = ? WHERE username = ? AND is_deleted = false",
            passwordEncoder.encode(adminPassword), "admin"
        );

        orgId = UuidGenerator.generate();
        jdbcTemplate.update(
            "INSERT INTO organizations (id, name, created_by) VALUES (?, ?, ?)",
            orgId, "Dashboard Metrics Org " + orgId, adminId
        );

        token = com.caseaxis.test.TestAuthCookies.loginCookie(mockMvc, objectMapper, "admin", adminPassword);
    }

    @Test
    void getMetrics_authenticated_returnsCorrectDashboardCounts() throws Exception {
        DashboardMetricsResponse before = getMetrics();

        String assignedOverdueId = createCase("Dashboard Assigned Overdue", "HIGH", "COMPLAINT", LocalDate.now().minusDays(1));
        var assignReq = new AssignCaseRequest(adminId, "Dashboard metrics assignment");
        mockMvc.perform(post("/api/cases/" + assignedOverdueId + "/assign")
                .cookie(token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(assignReq)))
            .andExpect(status().isOk());

        String escalatedId = createCase("Dashboard Escalated", "CRITICAL", "INVESTIGATION", LocalDate.now().plusDays(2));
        transition(escalatedId, "ESCALATED");

        String resolvedTodayId = createCase("Dashboard Resolved Today", "MEDIUM", "GENERAL", null);
        transition(resolvedTodayId, "ASSIGNED");
        transition(resolvedTodayId, "APPROVED");

        DashboardMetricsResponse after = getMetrics();

        assertThat(after.totalCases()).isEqualTo(before.totalCases() + 3);
        assertThat(after.openCases()).isEqualTo(before.openCases() + 2);
        assertThat(after.assignedToMe()).isEqualTo(before.assignedToMe() + 1);
        assertThat(after.overdueCases()).isEqualTo(before.overdueCases() + 1);
        assertThat(after.escalatedCases()).isEqualTo(before.escalatedCases() + 1);
        assertThat(after.closedToday()).isEqualTo(before.closedToday() + 1);
    }

    @Test
    void getOverview_authenticated_returnsOperationalWidgets() throws Exception {
        String assignedOverdueId = createCase("Dashboard Overview Assigned", "HIGH", "COMPLAINT", LocalDate.now().minusDays(2));
        var assignReq = new AssignCaseRequest(adminId, "Overview assignment");
        mockMvc.perform(post("/api/cases/" + assignedOverdueId + "/assign")
                .cookie(token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(assignReq)))
            .andExpect(status().isOk());

        String escalatedId = createCase("Dashboard Overview Escalated", "CRITICAL", "INVESTIGATION", LocalDate.now().plusDays(1));
        transition(escalatedId, "ESCALATED");

        mockMvc.perform(get("/api/dashboard/overview")
                .cookie(token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.metrics.totalCases").isNumber())
            .andExpect(jsonPath("$.data.recentAssignedCases").isArray())
            .andExpect(jsonPath("$.data.escalationWatch").isArray())
            .andExpect(jsonPath("$.data.overdueQueue").isArray())
            .andExpect(jsonPath("$.data.recentActivity").isArray());
    }

    @Test
    void getMetrics_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/dashboard/metrics"))
            .andExpect(status().isUnauthorized());
    }

    private String createCase(String title, String priorityCode, String typeCode, LocalDate dueDate) throws Exception {
        var req = new CreateCaseRequest(title, null, priorityCode, typeCode, orgId, null, dueDate);
        String resp = mockMvc.perform(post("/api/cases")
                .cookie(token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).at("/data/id").asText();
    }

    private void transition(String caseId, String targetCode) throws Exception {
        var req = new com.caseaxis.cases.TransitionStatusRequest(targetCode, null);
        mockMvc.perform(post("/api/cases/" + caseId + "/status")
                .cookie(token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk());
    }

    private DashboardMetricsResponse getMetrics() throws Exception {
        String resp = mockMvc.perform(get("/api/dashboard/metrics")
                .cookie(token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andReturn().getResponse().getContentAsString();
        return objectMapper.treeToValue(objectMapper.readTree(resp).at("/data"), DashboardMetricsResponse.class);
    }
}



