package com.caseaxis.search;

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

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SearchControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    @Autowired private UserRepository userRepository;

    private UUID adminId;
    private String token;
    private UUID orgId;

    @BeforeEach
    void setUp() throws Exception {
        adminId = userRepository.findByUsernameAndDeletedFalse("admin").orElseThrow().getId();

        orgId = UuidGenerator.generate();
        jdbcTemplate.update(
            "INSERT INTO organizations (id, name, created_by) VALUES (?, ?, ?)",
            orgId, "SearchTest Org " + orgId, adminId
        );

        String loginBody = objectMapper.writeValueAsString(new LoginRequest("admin", "admin"));
        String resp = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody))
            .andReturn().getResponse().getContentAsString();
        token = objectMapper.readTree(resp).at("/data/token").asText();
    }

    @Test
    void search_emptyQuery_returnsEmptyGroups() throws Exception {
        mockMvc.perform(get("/api/search")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.cases").isArray())
            .andExpect(jsonPath("$.data.clients").isArray())
            .andExpect(jsonPath("$.data.organizations").isArray())
            .andExpect(jsonPath("$.data.tasks").isArray());
    }

    @Test
    void search_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/search").param("q", "test"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void search_matchesCase() throws Exception {
        String unique = "GlobalSrch" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        var req = Map.of(
            "title", unique,
            "priorityCode", "MEDIUM",
            "typeCode", "GENERAL",
            "organizationId", orgId.toString()
        );
        mockMvc.perform(post("/api/cases")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/api/search")
                .param("q", unique)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.cases").isArray())
            .andExpect(jsonPath("$.data.cases[0].title").value(unique));
    }

    @Test
    void search_matchesOrganization() throws Exception {
        String unique = "SrchOrg" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        jdbcTemplate.update(
            "INSERT INTO organizations (id, name, created_by) VALUES (?, ?, ?)",
            UuidGenerator.generate(), unique, adminId
        );

        mockMvc.perform(get("/api/search")
                .param("q", unique)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.organizations[0].name").value(unique));
    }

    @Test
    void search_matchesClient() throws Exception {
        String unique = "SrchClt" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        jdbcTemplate.update("""
            INSERT INTO clients (id, organization_id, first_name, last_name, created_by, updated_by)
            VALUES (?, ?, ?, 'TestLast', ?, ?)
            """,
            UuidGenerator.generate(), orgId, unique, adminId, adminId
        );

        mockMvc.perform(get("/api/search")
                .param("q", unique)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.clients[0].displayName").value(unique + " TestLast"));
    }

    @Test
    void search_noMatch_returnsEmptyGroups() throws Exception {
        mockMvc.perform(get("/api/search")
                .param("q", "XYZZY_NO_MATCH_" + UUID.randomUUID())
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.cases").isEmpty())
            .andExpect(jsonPath("$.data.clients").isEmpty())
            .andExpect(jsonPath("$.data.organizations").isEmpty())
            .andExpect(jsonPath("$.data.tasks").isEmpty());
    }
}
