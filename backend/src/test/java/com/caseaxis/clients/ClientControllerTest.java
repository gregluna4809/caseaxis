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

import java.util.UUID;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ClientControllerTest {

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
    void listClients_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/clients"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void listClients_authenticated_returnsSuccessWithArray() throws Exception {
        mockMvc.perform(get("/api/clients")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void listClients_withActiveClient_includesItWithFormattedDisplayName() throws Exception {
        UUID clientId = UuidGenerator.generate();
        jdbcTemplate.update(
            "INSERT INTO clients (id, first_name, last_name, created_by) VALUES (?, ?, ?, ?)",
            clientId, "Jane", "Smith", adminId
        );

        String response = mockMvc.perform(get("/api/clients")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(response)
            .contains("CL-")
            .contains("Smith, Jane");
    }

    @Test
    void listClients_deletedClient_isExcludedFromResponse() throws Exception {
        UUID activeId = UuidGenerator.generate();
        jdbcTemplate.update(
            "INSERT INTO clients (id, first_name, last_name, created_by) VALUES (?, ?, ?, ?)",
            activeId, "Active", "Person", adminId
        );
        UUID deletedId = UuidGenerator.generate();
        jdbcTemplate.update(
            "INSERT INTO clients (id, first_name, last_name, is_deleted, created_by) VALUES (?, ?, ?, true, ?)",
            deletedId, "Deleted", "Person", adminId
        );

        String response = mockMvc.perform(get("/api/clients")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(response)
            .contains("Person, Active")
            .doesNotContain(deletedId.toString())
            .doesNotContain("Deleted, Person");
    }

    @Test
    void listClients_inactiveClient_isExcludedFromResponse() throws Exception {
        UUID activeId = UuidGenerator.generate();
        jdbcTemplate.update(
            "INSERT INTO clients (id, first_name, last_name, created_by) VALUES (?, ?, ?, ?)",
            activeId, "Active", "Client", adminId
        );
        UUID inactiveId = UuidGenerator.generate();
        jdbcTemplate.update(
            "INSERT INTO clients (id, first_name, last_name, is_active, created_by) VALUES (?, ?, ?, false, ?)",
            inactiveId, "Inactive", "Client", adminId
        );

        String response = mockMvc.perform(get("/api/clients")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(response)
            .contains("Client, Active")
            .doesNotContain(inactiveId.toString())
            .doesNotContain("Client, Inactive");
    }

    @Test
    void listClients_multipleClients_returnedSortedByLastNameThenFirstName() throws Exception {
        UUID bobId = UuidGenerator.generate();
        UUID aliceId = UuidGenerator.generate();
        UUID carolId = UuidGenerator.generate();
        jdbcTemplate.update(
            "INSERT INTO clients (id, first_name, last_name, created_by) VALUES (?, ?, ?, ?)",
            bobId, "Bob", "Smith", adminId
        );
        jdbcTemplate.update(
            "INSERT INTO clients (id, first_name, last_name, created_by) VALUES (?, ?, ?, ?)",
            aliceId, "Alice", "Smith", adminId
        );
        jdbcTemplate.update(
            "INSERT INTO clients (id, first_name, last_name, created_by) VALUES (?, ?, ?, ?)",
            carolId, "Carol", "Adams", adminId
        );

        String response = mockMvc.perform(get("/api/clients")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        // Adams, Carol must appear before Smith, Alice; Smith, Alice before Smith, Bob
        int carolIdx = response.indexOf(carolId.toString());
        int aliceIdx = response.indexOf(aliceId.toString());
        int bobIdx   = response.indexOf(bobId.toString());

        org.assertj.core.api.Assertions.assertThat(carolIdx).isLessThan(aliceIdx);
        org.assertj.core.api.Assertions.assertThat(aliceIdx).isLessThan(bobIdx);
    }

    @Test
    void listClients_returnsBusinessIdentifier() throws Exception {
        jdbcTemplate.update(
            "INSERT INTO clients (id, first_name, last_name, created_by) VALUES (?, ?, ?, ?)",
            UuidGenerator.generate(), "Business", "Client", adminId
        );

        mockMvc.perform(get("/api/clients")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[?(@.displayName=='Client, Business')].clientNumber").value(
                org.hamcrest.Matchers.hasItem(matchesPattern("CL-\\d{9}"))
            ));
    }
}
