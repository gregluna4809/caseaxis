package com.caseaxis.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void login_validCredentials_returnsToken() throws Exception {
        String body = objectMapper.writeValueAsString(new LoginRequest("admin", "admin"));
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.token").isNotEmpty());
    }

    @Test
    void login_invalidCredentials_returnsUnauthorized() throws Exception {
        String body = objectMapper.writeValueAsString(new LoginRequest("admin", "wrongpassword"));
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withoutToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/cases"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withValidToken_returns404NotUnauthorized() throws Exception {
        String body = objectMapper.writeValueAsString(new LoginRequest("admin", "admin"));
        String response = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(response).at("/data/token").asText();

        mockMvc.perform(get("/api/cases")
                .header("Authorization", "Bearer " + token))
            .andExpect(result ->
                assertNotEquals(401, result.getResponse().getStatus(),
                    "Valid JWT should not be rejected with 401"));
    }
}
