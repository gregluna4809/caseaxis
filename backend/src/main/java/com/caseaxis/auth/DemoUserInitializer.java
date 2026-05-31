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
public class DemoUserInitializer implements ApplicationRunner {

    private static final String DEMO_USERNAME = "demo";
    private static final String DEMO_PASSWORD = "demo123";
    private static final String DEMO_EMAIL = "demo@caseaxis.local";
    private static final String DEMO_ROLE_CODE = "SUPERVISOR";

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        UUID demoId = upsertDemoUser();
        UUID demoRoleId = findRoleId(DEMO_ROLE_CODE);

        removeAdminRole(demoId);
        assignDemoRoleIfMissing(demoId, demoRoleId);

        log.info("Public demo user is available: username={}, role={}", DEMO_USERNAME, DEMO_ROLE_CODE);
    }

    private UUID upsertDemoUser() {
        return jdbcTemplate.queryForObject("""
            INSERT INTO users (id, username, email, password_hash, first_name, last_name,
                               is_active, is_deleted, deleted_at, deleted_by, created_by, updated_by)
            VALUES (?, ?, ?, ?, ?, ?, true, false, null, null, null, null)
            ON CONFLICT (username) DO UPDATE SET
                email = EXCLUDED.email,
                password_hash = EXCLUDED.password_hash,
                first_name = EXCLUDED.first_name,
                last_name = EXCLUDED.last_name,
                is_active = true,
                is_deleted = false,
                deleted_at = null,
                deleted_by = null,
                updated_by = null
            RETURNING id
            """,
            UUID.class,
            UuidGenerator.generate(),
            DEMO_USERNAME,
            DEMO_EMAIL,
            passwordEncoder.encode(DEMO_PASSWORD),
            "Demo",
            "Reviewer"
        );
    }

    private UUID findRoleId(String roleCode) {
        return jdbcTemplate.queryForObject(
            "SELECT id FROM roles WHERE code = ? AND is_active = true",
            UUID.class,
            roleCode
        );
    }

    private void removeAdminRole(UUID demoId) {
        jdbcTemplate.update("""
            UPDATE user_roles ur
            SET removed_at = now(), removed_by = ?
            FROM roles r
            WHERE ur.role_id = r.id
              AND ur.user_id = ?
              AND ur.removed_at IS NULL
              AND r.code = 'ADMIN'
            """,
            demoId,
            demoId
        );
    }

    private void assignDemoRoleIfMissing(UUID demoId, UUID demoRoleId) {
        Integer activeRoleCount = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM user_roles
            WHERE user_id = ?
              AND role_id = ?
              AND removed_at IS NULL
            """,
            Integer.class,
            demoId,
            demoRoleId
        );

        if (activeRoleCount != null && activeRoleCount > 0) {
            return;
        }

        jdbcTemplate.update("""
            INSERT INTO user_roles (id, user_id, role_id, assigned_by)
            VALUES (?, ?, ?, ?)
            """,
            UuidGenerator.generate(),
            demoId,
            demoRoleId,
            demoId
        );
    }
}
