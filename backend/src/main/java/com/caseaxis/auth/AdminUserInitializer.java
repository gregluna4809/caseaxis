package com.caseaxis.auth;

import com.caseaxis.common.util.UuidGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(0)
public class AdminUserInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    @Value("${ADMIN_INITIAL_PASSWORD:}")
    private String initialAdminPassword;

    @Value("${ADMIN_INITIAL_USERNAME:admin}")
    private String initialAdminUsername;

    @Value("${ADMIN_INITIAL_EMAIL:admin@caseaxis.local}")
    private String initialAdminEmail;

    @Override
    public void run(ApplicationArguments args) {
        Integer adminCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM users WHERE username = ? AND is_deleted = false",
            Integer.class,
            initialAdminUsername
        );
        if (adminCount != null && adminCount > 0) {
            return;
        }

        if (initialAdminPassword == null || initialAdminPassword.isBlank()) {
            log.warn("No ADMIN_INITIAL_PASSWORD set; skipping admin user creation. Set ADMIN_INITIAL_PASSWORD to bootstrap an admin account.");
            return;
        }

        UUID adminId = UuidGenerator.generate();
        String passwordHash = passwordEncoder.encode(initialAdminPassword);

        jdbcTemplate.update("""
            INSERT INTO users (id, username, email, password_hash, first_name, last_name,
                               is_active, is_deleted)
            VALUES (?, ?, ?, ?, ?, ?, true, false)
            """,
            adminId, initialAdminUsername, initialAdminEmail, passwordHash, "Admin", "User");

        UUID adminRoleId = jdbcTemplate.queryForObject(
            "SELECT id FROM roles WHERE code = 'ADMIN' AND is_active = true",
            UUID.class);

        jdbcTemplate.update("""
            INSERT INTO user_roles (id, user_id, role_id, assigned_by)
            VALUES (?, ?, ?, ?)
            """,
            UuidGenerator.generate(), adminId, adminRoleId, adminId);

        log.info("Initial admin user created: username={}", initialAdminUsername);
    }
}
