package com.caseaxis.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityCorsPropertiesTest {

    @Test
    void defaultAllowedOriginsRemainProductionRestricted() {
        SecurityCorsProperties properties = new SecurityCorsProperties();

        assertThat(properties.getAllowedOrigins())
            .containsExactly(
                "https://caseaxis.pulse-forge.com",
                "https://staging.caseaxis.pulse-forge.com"
            )
            .doesNotContain(
                "http://localhost:5173",
                "http://127.0.0.1:5173",
                "http://localhost:5174",
                "http://127.0.0.1:5174"
            );
    }
}
