package com.caseaxis.security;

import com.caseaxis.users.User;
import com.caseaxis.users.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.DisabledException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CaseAxisUserDetailsServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final CaseAxisUserDetailsService service = new CaseAxisUserDetailsService(userRepository);

    @Test
    void rejectsDeactivatedUserWithoutLoadingRoles() {
        User user = user(false);
        when(userRepository.findByUsernameAndDeletedFalse("deactivated")).thenReturn(Optional.of(user));

        assertThrows(DisabledException.class, () -> service.loadUserByUsername("deactivated"));

        verify(userRepository, never()).findActiveRoleCodesByUserId(user.getId());
    }

    @Test
    void loadsActiveUserWithRoles() {
        User user = user(true);
        when(userRepository.findByUsernameAndDeletedFalse("active")).thenReturn(Optional.of(user));
        when(userRepository.findActiveRoleCodesByUserId(user.getId())).thenReturn(List.of("ADMIN"));

        var userDetails = service.loadUserByUsername("active");

        assertEquals("active", userDetails.getUsername());
        assertEquals(1, userDetails.getAuthorities().size());
    }

    private User user(boolean active) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername(active ? "active" : "deactivated");
        user.setPasswordHash("{noop}password");
        user.setActive(active);
        return user;
    }
}
