package com.caseaxis.health;

import com.caseaxis.security.CaseAxisUserDetailsService;
import com.caseaxis.security.JwtService;
import com.caseaxis.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HealthController.class)
@Import(SecurityConfig.class)
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // JwtAuthenticationFilter is a real Filter bean scanned by @WebMvcTest.
    // Mock its service-layer dependencies so the filter chain runs without a database.
    @MockBean
    private JwtService jwtService;

    @MockBean
    private CaseAxisUserDetailsService userDetailsService;

    @Test
    void health_returnsOkWithExpectedBody() throws Exception {
        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.service").value("caseaxis-backend"))
            .andExpect(jsonPath("$.data.status").value("UP"))
            .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void protectedEndpoint_returnsUnauthorizedWithoutCredentials() throws Exception {
        mockMvc.perform(get("/api/cases"))
            .andExpect(status().isUnauthorized());
    }
}
