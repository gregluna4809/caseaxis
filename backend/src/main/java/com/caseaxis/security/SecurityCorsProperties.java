package com.caseaxis.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "application.security.cors")
public class SecurityCorsProperties {

    private List<String> allowedOrigins = new ArrayList<>(List.of(
        "https://caseaxis.pulse-forge.com",
        "https://staging.caseaxis.pulse-forge.com"
    ));

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }
}
