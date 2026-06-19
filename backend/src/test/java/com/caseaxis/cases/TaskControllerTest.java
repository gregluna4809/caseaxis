package com.caseaxis.cases;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TaskControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @org.springframework.beans.factory.annotation.Value("${ADMIN_INITIAL_PASSWORD:admin}")
    private String adminPassword;
    @Autowired private JdbcTemplate jdbcTemplate;
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
            orgId, "Task Workspace Test Org " + orgId, adminId
        );

        token = com.caseaxis.test.TestAuthCookies.loginCookie(mockMvc, objectMapper, "admin", adminPassword);
    }

    // --- GET /api/tasks ---

    @Test
    void listTasks_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/tasks"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void listTasks_authenticated_returnsPaginatedPage() throws Exception {
        String caseId = createTestCase("Paginated Tasks Case");
        createTask(caseId, "Workspace Task One");
        createTask(caseId, "Workspace Task Two");

        mockMvc.perform(get("/api/tasks")
                .cookie(token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.content").isArray())
            .andExpect(jsonPath("$.data.totalElements").isNumber())
            .andExpect(jsonPath("$.data.totalPages").isNumber());
    }

    @Test
    void listTasks_searchByTitle_filtersResults() throws Exception {
        String caseId = createTestCase("Search Filter Case");
        createTask(caseId, "Zxqtaskworkspace Alpha");
        createTask(caseId, "Unrelated Task");

        mockMvc.perform(get("/api/tasks?q=Zxqtaskworkspace")
                .cookie(token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalElements").value(1))
            .andExpect(jsonPath("$.data.content[0].title").value("Zxqtaskworkspace Alpha"));
    }

    @Test
    void listTasks_searchIncludesCaseContext() throws Exception {
        String caseId = createTestCase("Context Case Zxqcasetitle");
        createTask(caseId, "Zxqtaskcasetitle Task");

        mockMvc.perform(get("/api/tasks?q=Zxqtaskcasetitle")
                .cookie(token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalElements").value(1))
            .andExpect(jsonPath("$.data.content[0].caseId").value(caseId))
            .andExpect(jsonPath("$.data.content[0].caseTitle").value("Context Case Zxqcasetitle"))
            .andExpect(jsonPath("$.data.content[0].caseNumber").isNotEmpty());
    }

    @Test
    void listTasks_filterByStatus_onlyReturnsMatchingStatus() throws Exception {
        String caseId = createTestCase("Status Filter Case");
        String taskId = createTask(caseId, "Zxqtaskstatus Pending Task");

        var updateReq = new UpdateCaseTaskRequest("Zxqtaskstatus Pending Task", null, "IN_PROGRESS", null, null);
        mockMvc.perform(put("/api/cases/" + caseId + "/tasks/" + taskId)
                .cookie(token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateReq)))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/tasks?q=Zxqtaskstatus&status=IN_PROGRESS")
                .cookie(token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalElements").value(1))
            .andExpect(jsonPath("$.data.content[0].statusCode").value("IN_PROGRESS"));

        mockMvc.perform(get("/api/tasks?q=Zxqtaskstatus&status=PENDING")
                .cookie(token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void listTasks_overdueOnly_returnsOverdueNonTerminalTasks() throws Exception {
        String caseId = createTestCase("Overdue Tasks Case");
        createTask(caseId, "Zxqtaskoverdue Past Due Task", LocalDate.now().minusDays(1));

        mockMvc.perform(get("/api/tasks?q=Zxqtaskoverdue&overdueOnly=true")
                .cookie(token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalElements").value(1))
            .andExpect(jsonPath("$.data.content[0].terminal").value(false));
    }

    @Test
    void listTasks_overdueOnly_excludesCompletedTasks() throws Exception {
        String caseId = createTestCase("Overdue Completed Case");
        String taskId = createTask(caseId, "Zxqtaskoverduedone Completed Task", LocalDate.now().minusDays(1));

        var completeReq = new UpdateCaseTaskRequest("Zxqtaskoverduedone Completed Task", null, "COMPLETED", null, null);
        mockMvc.perform(put("/api/cases/" + caseId + "/tasks/" + taskId)
                .cookie(token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(completeReq)))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/tasks?q=Zxqtaskoverduedone&overdueOnly=true")
                .cookie(token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    // --- GET /api/tasks/{id} ---

    @Test
    void getTask_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/tasks/" + UUID.randomUUID()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void getTask_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/tasks/" + UUID.randomUUID())
                .cookie(token))
            .andExpect(status().isNotFound());
    }

    @Test
    void getTask_existingTask_returnsCaseDetails() throws Exception {
        String caseId = createTestCase("Detail Case Zxqtaskdetail");
        String taskId = createTask(caseId, "Zxqtaskdetail Detail Task");

        mockMvc.perform(get("/api/tasks/" + taskId)
                .cookie(token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(taskId))
            .andExpect(jsonPath("$.data.title").value("Zxqtaskdetail Detail Task"))
            .andExpect(jsonPath("$.data.caseId").value(caseId))
            .andExpect(jsonPath("$.data.caseTitle").value("Detail Case Zxqtaskdetail"))
            .andExpect(jsonPath("$.data.caseNumber").isNotEmpty())
            .andExpect(jsonPath("$.data.statusCode").value("PENDING"))
            .andExpect(jsonPath("$.data.terminal").value(false));
    }

    // --- PUT /api/tasks/{id} ---

    @Test
    void updateTask_unauthenticated_returns401() throws Exception {
        mockMvc.perform(put("/api/tasks/" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new UpdateCaseTaskRequest("title", null, "IN_PROGRESS", null, null))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void updateTask_changeStatus_updatesTask() throws Exception {
        String caseId = createTestCase("Workspace Update Case");
        String taskId = createTask(caseId, "Workspace Updatable Task");

        var req = new UpdateCaseTaskRequest("Workspace Updatable Task", "New description", "IN_PROGRESS", null, null);
        mockMvc.perform(put("/api/tasks/" + taskId)
                .cookie(token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.statusCode").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.data.description").value("New description"));
    }

    @Test
    void updateTask_notFound_returns404() throws Exception {
        var req = new UpdateCaseTaskRequest("title", null, "IN_PROGRESS", null, null);
        mockMvc.perform(put("/api/tasks/" + UUID.randomUUID())
                .cookie(token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteTask_softDeletesFromWorkspace() throws Exception {
        String caseId = createTestCase("Workspace Delete Case");
        String taskId = createTask(caseId, "Zxqtaskdelete Delete Task");

        mockMvc.perform(delete("/api/tasks/" + taskId)
                .cookie(token))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/tasks?q=Zxqtaskdelete")
                .cookie(token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    // --- Helpers ---

    private String createTestCase(String title) throws Exception {
        var req = new CreateCaseRequest(title, null, "LOW", "GENERAL", orgId, null, null);
        String resp = mockMvc.perform(post("/api/cases")
                .cookie(token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).at("/data/id").asText();
    }

    private String createTask(String caseId, String title) throws Exception {
        return createTask(caseId, title, null);
    }

    private String createTask(String caseId, String title, LocalDate dueDate) throws Exception {
        var req = new CreateCaseTaskRequest(title, null, null, null, dueDate);
        String resp = mockMvc.perform(post("/api/cases/" + caseId + "/tasks")
                .cookie(token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).at("/data/id").asText();
    }
}



