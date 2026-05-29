package com.caseaxis.clients;

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

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ClientControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @org.springframework.beans.factory.annotation.Value("${ADMIN_INITIAL_PASSWORD:admin}")
    private String adminPassword;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    @Autowired private UserRepository userRepository;

    private UUID adminId;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        adminId = userRepository.findByUsernameAndDeletedFalse("admin").orElseThrow().getId();
        jdbcTemplate.update(
            "UPDATE users SET password_hash = ? WHERE username = ? AND is_deleted = false",
            passwordEncoder.encode(adminPassword), "admin"
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
    void listClients_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/clients"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void listClients_authenticated_returnsPaginatedPage() throws Exception {
        mockMvc.perform(get("/api/clients")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.content").isArray())
            .andExpect(jsonPath("$.data.totalElements").isNumber());
    }

    @Test
    void listClients_defaultSort_returnsNewestCreatedFirst() throws Exception {
        UUID olderId = UuidGenerator.generate();
        UUID newerId = UuidGenerator.generate();
        OffsetDateTime olderCreated = OffsetDateTime.now().minusDays(10);
        OffsetDateTime newerCreated = OffsetDateTime.now().minusDays(1);

        jdbcTemplate.update(
            "INSERT INTO clients (id, first_name, last_name, created_at, updated_at, created_by) VALUES (?, ?, ?, ?, ?, ?)",
            olderId, "Older", "Aardvark", olderCreated, olderCreated, adminId
        );
        jdbcTemplate.update(
            "INSERT INTO clients (id, first_name, last_name, created_at, updated_at, created_by) VALUES (?, ?, ?, ?, ?, ?)",
            newerId, "Newer", "Zulu", newerCreated, newerCreated, adminId
        );

        mockMvc.perform(get("/api/clients?page=0&size=1")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].displayName").value("Zulu, Newer"));
    }

    @Test
    void listClients_withActiveClient_includesItWithFormattedDisplayName() throws Exception {
        UUID clientId = UuidGenerator.generate();
        jdbcTemplate.update(
            "INSERT INTO clients (id, first_name, last_name, created_by) VALUES (?, ?, ?, ?)",
            clientId, "Jane", "Zxqtest", adminId
        );

        String response = mockMvc.perform(get("/api/clients?q=Zxqtest")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(response)
            .contains("CL-")
            .contains("Zxqtest, Jane");
    }

    @Test
    void listClients_deletedClient_isExcludedFromResponse() throws Exception {
        UUID activeId = UuidGenerator.generate();
        jdbcTemplate.update(
            "INSERT INTO clients (id, first_name, last_name, created_by) VALUES (?, ?, ?, ?)",
            activeId, "Active", "Zxqperson", adminId
        );
        UUID deletedId = UuidGenerator.generate();
        jdbcTemplate.update(
            "INSERT INTO clients (id, first_name, last_name, is_deleted, created_by) VALUES (?, ?, ?, true, ?)",
            deletedId, "Deleted", "Zxqperson", adminId
        );

        String response = mockMvc.perform(get("/api/clients?q=Zxqperson")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(response)
            .contains("Zxqperson, Active")
            .doesNotContain(deletedId.toString())
            .doesNotContain("Deleted, Zxqperson");
    }

    @Test
    void listClients_searchByName_filtersResults() throws Exception {
        UUID targetId = UuidGenerator.generate();
        jdbcTemplate.update(
            "INSERT INTO clients (id, first_name, last_name, created_by) VALUES (?, ?, ?, ?)",
            targetId, "Alice", "Wonderland", adminId
        );
        UUID otherId = UuidGenerator.generate();
        jdbcTemplate.update(
            "INSERT INTO clients (id, first_name, last_name, created_by) VALUES (?, ?, ?, ?)",
            otherId, "Bob", "Builder", adminId
        );

        String response = mockMvc.perform(get("/api/clients?q=Wonderland")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(response)
            .contains("Wonderland, Alice")
            .doesNotContain("Builder, Bob");
    }

    @Test
    void listClients_filterByOrganization_returnsOnlyMatchingClients() throws Exception {
        UUID orgId = UuidGenerator.generate();
        jdbcTemplate.update(
            "INSERT INTO organizations (id, name, created_by) VALUES (?, ?, ?)",
            orgId, "Filter Test Org", adminId
        );
        UUID orgClientId = UuidGenerator.generate();
        jdbcTemplate.update(
            "INSERT INTO clients (id, organization_id, first_name, last_name, created_by) VALUES (?, ?, ?, ?, ?)",
            orgClientId, orgId, "Org", "Member", adminId
        );
        UUID otherClientId = UuidGenerator.generate();
        jdbcTemplate.update(
            "INSERT INTO clients (id, first_name, last_name, created_by) VALUES (?, ?, ?, ?)",
            otherClientId, "No", "Org", adminId
        );

        String response = mockMvc.perform(get("/api/clients?organizationId=" + orgId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(response)
            .contains("Member, Org")
            .doesNotContain("Org, No");
    }

    @Test
    void listClients_returnsBusinessIdentifier() throws Exception {
        jdbcTemplate.update(
            "INSERT INTO clients (id, first_name, last_name, created_by) VALUES (?, ?, ?, ?)",
            UuidGenerator.generate(), "Business", "Zxqclient", adminId
        );

        mockMvc.perform(get("/api/clients?q=Zxqclient")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[?(@.displayName=='Zxqclient, Business')].clientNumber").value(
                org.hamcrest.Matchers.hasItem(matchesPattern("CL-\\d{9}"))
            ));
    }

    @Test
    void getClientById_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/clients/" + UUID.randomUUID()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void getClientById_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/clients/" + UUID.randomUUID())
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound());
    }

    @Test
    void getClientById_existingClient_returnsDetailWithStats() throws Exception {
        UUID clientId = UuidGenerator.generate();
        jdbcTemplate.update(
            "INSERT INTO clients (id, first_name, last_name, created_by) VALUES (?, ?, ?, ?)",
            clientId, "Detail", "Test", adminId
        );

        mockMvc.perform(get("/api/clients/" + clientId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.displayName").value("Test, Detail"))
            .andExpect(jsonPath("$.data.totalCases").isNumber())
            .andExpect(jsonPath("$.data.openCases").isNumber())
            .andExpect(jsonPath("$.data.escalatedCases").isNumber())
            .andExpect(jsonPath("$.data.overdueCases").isNumber());
    }

    @Test
    void deactivateClient_marksClientInactiveAndPreservesDetail() throws Exception {
        UUID clientId = UuidGenerator.generate();
        jdbcTemplate.update(
            "INSERT INTO clients (id, first_name, last_name, created_by) VALUES (?, ?, ?, ?)",
            clientId, "Deactivate", "Client", adminId
        );

        mockMvc.perform(post("/api/clients/" + clientId + "/deactivate")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.active").value(false))
            .andExpect(jsonPath("$.data.displayName").value("Client, Deactivate"));

        mockMvc.perform(get("/api/clients?active=true&q=Client")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[?(@.id=='" + clientId + "')]").isEmpty());
    }

    @Test
    void listClientCases_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/clients/" + UUID.randomUUID() + "/cases"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void listClientCases_notFoundClient_returns404() throws Exception {
        mockMvc.perform(get("/api/clients/" + UUID.randomUUID() + "/cases")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound());
    }

    @Test
    void listClientCases_existingClient_returnsPaginatedCases() throws Exception {
        UUID clientId = UuidGenerator.generate();
        jdbcTemplate.update(
            "INSERT INTO clients (id, first_name, last_name, created_by) VALUES (?, ?, ?, ?)",
            clientId, "Cases", "Owner", adminId
        );

        mockMvc.perform(get("/api/clients/" + clientId + "/cases")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.content").isArray());
    }
}
