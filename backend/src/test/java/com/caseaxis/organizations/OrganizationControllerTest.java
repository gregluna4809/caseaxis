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
    void listOrganizations_authenticated_returnsSuccessWithArray() throws Exception {
        mockMvc.perform(get("/api/organizations")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").isArray());
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
    void listOrganizations_inactiveOrg_isExcludedFromResponse() throws Exception {
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

        String response = mockMvc.perform(get("/api/organizations")
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
            UuidGenerator.generate(), "Business Id Org", adminId
        );

        mockMvc.perform(get("/api/organizations")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[?(@.name=='Business Id Org')].organizationCode").value(
                org.hamcrest.Matchers.hasItem(matchesPattern("ORG-\\d{9}"))
            ));
    }

    @Test
    void listOrganizations_multipleOrgs_returnedInAlphabeticalOrder() throws Exception {
        UUID zId = UuidGenerator.generate();
        UUID aId = UuidGenerator.generate();
        jdbcTemplate.update(
            "INSERT INTO organizations (id, name, created_by) VALUES (?, ?, ?)",
            zId, "Zephyr Inc", adminId
        );
        jdbcTemplate.update(
            "INSERT INTO organizations (id, name, created_by) VALUES (?, ?, ?)",
            aId, "Alpha Ltd", adminId
        );

        String response = mockMvc.perform(get("/api/organizations")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        // Alpha Ltd must appear before Zephyr Inc in the JSON string
        int alphaIdx = response.indexOf("Alpha Ltd");
        int zephyrIdx = response.indexOf("Zephyr Inc");
        org.assertj.core.api.Assertions.assertThat(alphaIdx)
            .isGreaterThanOrEqualTo(0)
            .isLessThan(zephyrIdx);
    }
}
