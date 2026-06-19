package com.caseaxis.test;

import com.caseaxis.auth.LoginRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public final class TestAuthCookies {

    private static final String TOKEN_COOKIE_NAME = "token";

    private TestAuthCookies() {
    }

    public static Cookie loginCookie(
        MockMvc mockMvc,
        ObjectMapper objectMapper,
        String username,
        String password
    ) throws Exception {
        var response = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(username, password))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();

        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        if (setCookie == null || setCookie.isBlank()) {
            throw new AssertionError("Login response did not include a Set-Cookie header");
        }

        String prefix = TOKEN_COOKIE_NAME + "=";
        for (String part : setCookie.split(";")) {
            String trimmed = part.trim();
            if (trimmed.startsWith(prefix)) {
                String token = trimmed.substring(prefix.length());
                if (token.isBlank()) {
                    throw new AssertionError("Login Set-Cookie header included a blank token value");
                }
                return new Cookie(TOKEN_COOKIE_NAME, token);
            }
        }

        throw new AssertionError("Login Set-Cookie header did not include token cookie");
    }
}
