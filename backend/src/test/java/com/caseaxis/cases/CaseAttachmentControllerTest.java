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
class CaseAttachmentControllerTest {

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
            orgId, "Attachment Test Org " + orgId, adminId
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
    void registerAttachment_validRequest_returnsCreated() throws Exception {
        String caseId = createTestCase("Attachment Creation Test");

        var req = new CreateCaseAttachmentRequest(
            "report.pdf", "/uploads/2026/report.pdf", 204800L, "application/pdf", "Annual report"
        );
        mockMvc.perform(post("/api/cases/" + caseId + "/attachments")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.originalFilename").value("report.pdf"))
            .andExpect(jsonPath("$.data.mimeType").value("application/pdf"))
            .andExpect(jsonPath("$.data.fileSizeBytes").value(204800))
            .andExpect(jsonPath("$.data.caseId").value(caseId))
            .andExpect(jsonPath("$.data.createdAt").isNotEmpty());
    }

    @Test
    void registerAttachment_minimalRequest_returns201() throws Exception {
        String caseId = createTestCase("Minimal Attachment Test");

        var req = new CreateCaseAttachmentRequest("doc.txt", "/store/doc.txt", null, null, null);
        mockMvc.perform(post("/api/cases/" + caseId + "/attachments")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.originalFilename").value("doc.txt"));
    }

    @Test
    void registerAttachment_missingStoragePath_returns400() throws Exception {
        String caseId = createTestCase("Validation Attachment Case");

        var req = new CreateCaseAttachmentRequest("file.txt", "", null, null, null);
        mockMvc.perform(post("/api/cases/" + caseId + "/attachments")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void registerAttachment_caseNotFound_returns404() throws Exception {
        var req = new CreateCaseAttachmentRequest("file.txt", "/store/file.txt", null, null, null);
        mockMvc.perform(post("/api/cases/" + UUID.randomUUID() + "/attachments")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isNotFound());
    }

    @Test
    void listAttachments_returnsAttachmentsForCase() throws Exception {
        String caseId = createTestCase("List Attachments Case");
        registerAttachment(caseId, "file1.pdf", "/store/file1.pdf");
        registerAttachment(caseId, "file2.pdf", "/store/file2.pdf");

        mockMvc.perform(get("/api/cases/" + caseId + "/attachments")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
    void listAttachments_caseNotFound_returns404() throws Exception {
        mockMvc.perform(get("/api/cases/" + UUID.randomUUID() + "/attachments")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteAttachment_existingAttachment_returns200AndExcludesFromList() throws Exception {
        String caseId = createTestCase("Delete Attachment Case");
        String attachmentId = registerAttachment(caseId, "delete-me.pdf", "/store/delete-me.pdf");

        mockMvc.perform(delete("/api/cases/" + caseId + "/attachments/" + attachmentId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/cases/" + caseId + "/attachments")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void deleteAttachment_nonexistentAttachment_returns404() throws Exception {
        String caseId = createTestCase("Delete 404 Attachment Case");

        mockMvc.perform(delete("/api/cases/" + caseId + "/attachments/" + UUID.randomUUID())
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

    private String registerAttachment(String caseId, String filename, String storagePath) throws Exception {
        var req = new CreateCaseAttachmentRequest(filename, storagePath, null, null, null);
        String resp = mockMvc.perform(post("/api/cases/" + caseId + "/attachments")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).at("/data/id").asText();
    }
}
