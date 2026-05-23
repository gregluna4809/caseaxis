package com.caseaxis.organizations;

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

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrganizationControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private UserRepository userRepository;

    private UUID adminId;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        adminId = userRepository.findByUsernameAndDeletedFalse("admin").orElseThrow().getId();

        String loginBody = objectMapper.writeValueAsString(new LoginRequest("admin", "admin"));
        String resp = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody))
            .andReturn().getResponse().getContentAsString();
        token = objectMapper.readTree(resp).at("/data/token").asText();
    }

    @Test
    void listOrganizations_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/organizations"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void listOrganizations_authenticated_returnsPaginatedPage() throws Exception {
        mockMvc.perform(get("/api/organizations")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.content").isArray())
            .andExpect(jsonPath("$.data.totalElements").isNumber());
    }

    @Test
    void listOrganizations_withActiveOrg_includesItInResponse() throws Exception {
        UUID orgId = UuidGenerator.generate();
        jdbcTemplate.update(
            "INSERT INTO organizations (id, name, created_by) VALUES (?, ?, ?)",
            orgId, "Acme Corp", adminId
        );

        String response = mockMvc.perform(get("/api/organizations")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(response)
            .contains("ORG-")
            .contains("Acme Corp");
    }

    @Test
    void listOrganizations_deletedOrg_isExcludedFromResponse() throws Exception {
        UUID activeId = UuidGenerator.generate();
        jdbcTemplate.update(
            "INSERT INTO organizations (id, name, created_by) VALUES (?, ?, ?)",
            activeId, "Active Corp", adminId
        );
        UUID deletedId = UuidGenerator.generate();
        jdbcTemplate.update(
            "INSERT INTO organizations (id, name, is_deleted, created_by) VALUES (?, ?, true, ?)",
            deletedId, "Deleted Corp", adminId
        );

        String response = mockMvc.perform(get("/api/organizations")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(response)
            .contains("Active Corp")
            .doesNotContain(deletedId.toString())
            .doesNotContain("Deleted Corp");
    }

    @Test
    void listOrganizations_inactiveOrg_isExcludedByDefault() throws Exception {
        UUID activeId = UuidGenerator.generate();
        jdbcTemplate.update(
            "INSERT INTO organizations (id, name, created_by) VALUES (?, ?, ?)",
            activeId, "Active Org", adminId
        );
        UUID inactiveId = UuidGenerator.generate();
        jdbcTemplate.update(
            "INSERT INTO organizations (id, name, is_active, created_by) VALUES (?, ?, false, ?)",
            inactiveId, "Inactive Org", adminId
        );

        String response = mockMvc.perform(get("/api/organizations?active=true")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(response)
            .contains("Active Org")
            .doesNotContain(inactiveId.toString())
            .doesNotContain("Inactive Org");
    }

    @Test
    void listOrganizations_returnsBusinessIdentifier() throws Exception {
        jdbcTemplate.update(
            "INSERT INTO organizations (id, name, created_by) VALUES (?, ?, ?)",
            UuidGenerator.generate(), "Zxqbusiness Org", adminId
        );

        mockMvc.perform(get("/api/organizations?q=Zxqbusiness")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[?(@.name=='Zxqbusiness Org')].organizationCode").value(
                org.hamcrest.Matchers.hasItem(matchesPattern("ORG-\\d{9}"))
            ));
    }

    @Test
    void listOrganizations_searchByName_filtersResults() throws Exception {
        UUID targetId = UuidGenerator.generate();
        jdbcTemplate.update(
            "INSERT INTO organizations (id, name, created_by) VALUES (?, ?, ?)",
            targetId, "Zenith Industries", adminId
        );
        UUID otherId = UuidGenerator.generate();
        jdbcTemplate.update(
            "INSERT INTO organizations (id, name, created_by) VALUES (?, ?, ?)",
            otherId, "Alpha Corp", adminId
        );

        String response = mockMvc.perform(get("/api/organizations?q=Zenith")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(response)
            .contains("Zenith Industries")
            .doesNotContain("Alpha Corp");
    }

    @Test
    void getOrganizationById_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/organizations/" + UUID.randomUUID()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void getOrganizationById_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/organizations/" + UUID.randomUUID())
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound());
    }

    @Test
    void getOrganizationById_existingOrg_returnsDetailWithMetrics() throws Exception {
        UUID orgId = UuidGenerator.generate();
        jdbcTemplate.update(
            "INSERT INTO organizations (id, name, created_by) VALUES (?, ?, ?)",
            orgId, "Detail Org", adminId
        );

        mockMvc.perform(get("/api/organizations/" + orgId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.name").value("Detail Org"))
            .andExpect(jsonPath("$.data.clientCount").isNumber())
            .andExpect(jsonPath("$.data.caseCount").isNumber())
            .andExpect(jsonPath("$.data.openCaseCount").isNumber());
    }

    @Test
    void deactivateOrganization_withoutActiveDependencies_marksInactive() throws Exception {
        UUID orgId = UuidGenerator.generate();
        jdbcTemplate.update(
            "INSERT INTO organizations (id, name, created_by) VALUES (?, ?, ?)",
            orgId, "Deactivate Org", adminId
        );

        mockMvc.perform(post("/api/organizations/" + orgId + "/deactivate")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.active").value(false))
            .andExpect(jsonPath("$.data.name").value("Deactivate Org"));
    }

    @Test
    void deactivateOrganization_withActiveClient_returns409() throws Exception {
        UUID orgId = UuidGenerator.generate();
        jdbcTemplate.update(
            "INSERT INTO organizations (id, name, created_by) VALUES (?, ?, ?)",
            orgId, "Protected Org", adminId
        );
        jdbcTemplate.update(
            "INSERT INTO clients (id, organization_id, first_name, last_name, created_by) VALUES (?, ?, ?, ?, ?)",
            UuidGenerator.generate(), orgId, "Active", "Dependency", adminId
        );

        mockMvc.perform(post("/api/organizations/" + orgId + "/deactivate")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isConflict());
    }

    @Test
    void listOrganizationClients_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/organizations/" + UUID.randomUUID() + "/clients"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void listOrganizationClients_notFoundOrg_returns404() throws Exception {
        mockMvc.perform(get("/api/organizations/" + UUID.randomUUID() + "/clients")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound());
    }

    @Test
    void listOrganizationClients_returnsPagedClientsForOrg() throws Exception {
        UUID orgId = UuidGenerator.generate();
        jdbcTemplate.update(
            "INSERT INTO organizations (id, name, created_by) VALUES (?, ?, ?)",
            orgId, "Clients Test Org", adminId
        );
        UUID clientId = UuidGenerator.generate();
        jdbcTemplate.update(
            "INSERT INTO clients (id, organization_id, first_name, last_name, created_by) VALUES (?, ?, ?, ?, ?)",
            clientId, orgId, "Test", "Client", adminId
        );

        mockMvc.perform(get("/api/organizations/" + orgId + "/clients")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.content").isArray())
            .andExpect(jsonPath("$.data.content[0].displayName").value("Client, Test"));
    }
}
