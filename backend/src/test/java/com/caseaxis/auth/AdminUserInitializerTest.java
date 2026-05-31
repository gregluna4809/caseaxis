package com.caseaxis.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminUserInitializerTest {

    @Test
    void createsAdminWhenOtherUsersExistButConfiguredAdminIsMissing() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        AdminUserInitializer initializer = new AdminUserInitializer(jdbcTemplate, passwordEncoder);
        UUID adminRoleId = UUID.randomUUID();

        ReflectionTestUtils.setField(initializer, "initialAdminUsername", "admin");
        ReflectionTestUtils.setField(initializer, "initialAdminEmail", "admin@caseaxis.local");
        ReflectionTestUtils.setField(initializer, "initialAdminPassword", "admin-password");

        when(jdbcTemplate.queryForObject(contains("username = ?"), eq(Integer.class), eq("admin")))
            .thenReturn(0);
        when(passwordEncoder.encode("admin-password")).thenReturn("encoded-password");
        when(jdbcTemplate.queryForObject(contains("code = 'ADMIN'"), eq(UUID.class)))
            .thenReturn(adminRoleId);

        initializer.run(mock(ApplicationArguments.class));

        verify(jdbcTemplate).update(contains("INSERT INTO users"), any(), eq("admin"),
            eq("admin@caseaxis.local"), eq("encoded-password"), eq("Admin"), eq("User"));
        verify(jdbcTemplate).update(contains("INSERT INTO user_roles"), any(), any(), eq(adminRoleId), any());
    }

    @Test
    void doesNotCreateAdminWhenConfiguredAdminAlreadyExists() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        AdminUserInitializer initializer = new AdminUserInitializer(jdbcTemplate, passwordEncoder);

        ReflectionTestUtils.setField(initializer, "initialAdminUsername", "admin");
        ReflectionTestUtils.setField(initializer, "initialAdminPassword", "admin-password");

        when(jdbcTemplate.queryForObject(contains("username = ?"), eq(Integer.class), eq("admin")))
            .thenReturn(1);

        initializer.run(mock(ApplicationArguments.class));

        verify(passwordEncoder, never()).encode(any());
        verify(jdbcTemplate, never()).queryForObject(contains("code = 'ADMIN'"), eq(UUID.class));
    }
}
