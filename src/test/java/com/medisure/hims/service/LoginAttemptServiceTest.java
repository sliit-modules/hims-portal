package com.medisure.hims.service;

import com.medisure.hims.config.UserPrincipal;
import com.medisure.hims.model.Role;
import com.medisure.hims.model.User;
import com.medisure.hims.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private LoginAttemptService loginAttemptService;

    private User member;

    @BeforeEach
    void setUp() {
        member = new User();
        member.setId(7L);
        member.setNic("199009098765");
        member.setFullName("Kasun Perera");
        member.setRole(Role.POLICYHOLDER);
    }

    @Test
    @DisplayName("Wrong passwords are counted without locking before the limit")
    void countsFailuresBelowTheLimit() {
        member.setFailedLoginAttempts(3);
        when(userRepository.findByNic("199009098765")).thenReturn(Optional.of(member));

        boolean locked = loginAttemptService.recordFailure("199009098765");

        assertFalse(locked);
        assertEquals(4, member.getFailedLoginAttempts());
        assertFalse(member.isLocked());
        verify(userRepository).save(member);
    }

    @Test
    @DisplayName("The fifth wrong password in a row locks the account for 15 minutes")
    void locksOnTheFifthFailure() {
        member.setFailedLoginAttempts(4);
        when(userRepository.findByNic("199009098765")).thenReturn(Optional.of(member));

        boolean locked = loginAttemptService.recordFailure("199009098765");

        assertTrue(locked);
        assertTrue(member.isLocked());
        assertTrue(member.getLockedUntil().isAfter(LocalDateTime.now().plusMinutes(14)));
        verify(auditService).log(eq("User"), eq(7L), eq("LOCKED"), isNull(), any());
    }

    @Test
    @DisplayName("A locked account stays locked and is not counted again")
    void lockedAccountIsNotCountedAgain() {
        member.setLockedUntil(LocalDateTime.now().plusMinutes(10));
        when(userRepository.findByNic("199009098765")).thenReturn(Optional.of(member));

        assertTrue(loginAttemptService.recordFailure("199009098765"));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("An unknown NIC or email is ignored")
    void ignoresUnknownIdentifier() {
        when(userRepository.findByNic("nobody@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertFalse(loginAttemptService.recordFailure("nobody@example.com"));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("A successful sign-in clears the count of wrong passwords")
    void successClearsTheCount() {
        member.setFailedLoginAttempts(3);
        when(userRepository.findById(7L)).thenReturn(Optional.of(member));

        loginAttemptService.recordSuccess(member);

        assertEquals(0, member.getFailedLoginAttempts());
        verify(userRepository).save(member);
    }

    @Test
    @DisplayName("Spring Security sees the account as locked only while the lock is running")
    void principalReportsLockUntilItExpires() {
        member.setLockedUntil(LocalDateTime.now().plusMinutes(5));
        assertFalse(new UserPrincipal(member).isAccountNonLocked());

        member.setLockedUntil(LocalDateTime.now().minusMinutes(1));
        assertTrue(new UserPrincipal(member).isAccountNonLocked());
    }
}
