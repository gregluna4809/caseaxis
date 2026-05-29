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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CaseNoteControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @org.springframework.beans.factory.annotation.Value("${ADMIN_INITIAL_PASSWORD:admin}")
    private String adminPassword;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    @Autowired private UserRepository userRepository;

    private UUID orgId;
    private UUID adminId;
    private String token;

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
            orgId, "Note Test Org " + orgId, adminId
        );

        String loginBody = objectMapper.writeValueAsString(new LoginRequest("admin", adminPassword));
        String resp = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody))
            .andReturn().getResponse().getContentAsString();
        token = objectMapper.readTree(resp).at("/data/token").asText();
        org.assertj.core.api.Assertions.assertThat(token).isNotBlank();
    }

    @Test
    void createNote_validRequest_returnsCreated() throws Exception {
        String caseId = createTestCase("Note Test Case");

        var req = new CreateCaseNoteRequest("First note body", false, null);
        mockMvc.perform(post("/api/cases/" + caseId + "/notes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.body").value("First note body"))
            .andExpect(jsonPath("$.data.internal").value(false))
            .andExpect(jsonPath("$.data.caseId").value(caseId))
            .andExpect(jsonPath("$.data.createdAt").isNotEmpty());
    }

    @Test
    void createNote_internalFlag_persistsCorrectly() throws Exception {
        String caseId = createTestCase("Internal Note Case");

        var req = new CreateCaseNoteRequest("Internal note body", true, null);
        mockMvc.perform(post("/api/cases/" + caseId + "/notes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.internal").value(true));
    }

    @Test
    void createNote_emptyBody_returns400() throws Exception {
        String caseId = createTestCase("Validation Test Case");

        var req = new CreateCaseNoteRequest("", false, null);
        mockMvc.perform(post("/api/cases/" + caseId + "/notes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createNote_caseNotFound_returns404() throws Exception {
        var req = new CreateCaseNoteRequest("Orphan note", false, null);
        mockMvc.perform(post("/api/cases/" + UUID.randomUUID() + "/notes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isNotFound());
    }

    @Test
    void listNotes_returnsNotesForCase() throws Exception {
        String caseId = createTestCase("List Notes Case");
        createNote(caseId, "Note 1");
        createNote(caseId, "Note 2");

        mockMvc.perform(get("/api/cases/" + caseId + "/notes")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
    void listNotes_caseNotFound_returns404() throws Exception {
        mockMvc.perform(get("/api/cases/" + UUID.randomUUID() + "/notes")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteNote_existingNote_returns200AndExcludesFromList() throws Exception {
        String caseId = createTestCase("Delete Note Case");
        String noteId = createNote(caseId, "Note to delete");

        mockMvc.perform(delete("/api/cases/" + caseId + "/notes/" + noteId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        // Soft-deleted note should not appear in list
        mockMvc.perform(get("/api/cases/" + caseId + "/notes")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void deleteNote_nonexistentNote_returns404() throws Exception {
        String caseId = createTestCase("Delete 404 Case");

        mockMvc.perform(delete("/api/cases/" + caseId + "/notes/" + UUID.randomUUID())
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound());
    }

    @Test
    void createNote_unauthenticated_returns401() throws Exception {
        String caseId = createTestCase("Auth Test Case");
        var req = new CreateCaseNoteRequest("Unauthorized note", false, null);
        mockMvc.perform(post("/api/cases/" + caseId + "/notes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isUnauthorized());
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

    private String createNote(String caseId, String body) throws Exception {
        var req = new CreateCaseNoteRequest(body, false, null);
        String resp = mockMvc.perform(post("/api/cases/" + caseId + "/notes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).at("/data/id").asText();
    }
}
