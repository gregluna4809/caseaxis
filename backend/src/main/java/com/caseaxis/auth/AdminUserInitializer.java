package com.caseaxis.auth;

import com.caseaxis.common.util.UuidGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminUserInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        Integer userCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM users WHERE is_deleted = false", Integer.class);
        if (userCount != null && userCount > 0) {
            return;
        }

        UUID adminId = UuidGenerator.generate();
        String passwordHash = passwordEncoder.encode("admin");

        jdbcTemplate.update("""
            INSERT INTO users (id, username, email, password_hash, first_name, last_name,
                               is_active, is_deleted)
            VALUES (?, ?, ?, ?, ?, ?, true, false)
            """,
            adminId, "admin", "admin@caseaxis.local", passwordHash, "Admin", "User");

        UUID adminRoleId = jdbcTemplate.queryForObject(
            "SELECT id FROM roles WHERE code = 'ADMIN' AND is_active = true",
            UUID.class);

        jdbcTemplate.update("""
            INSERT INTO user_roles (id, user_id, role_id, assigned_by)
            VALUES (?, ?, ?, ?)
            """,
            UuidGenerator.generate(), adminId, adminRoleId, adminId);

        log.info("Dev admin user created: username=admin");
    }
}
