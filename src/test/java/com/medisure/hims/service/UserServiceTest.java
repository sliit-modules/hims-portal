package com.medisure.hims.service;

import com.medisure.hims.model.*;
import com.medisure.hims.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private UserService userService;

    private User newMember() {
        User member = new User();
        member.setNic("200012345678");
        member.setFullName("Tharushi Silva");
        member.setDateOfBirth(LocalDate.of(2000, 5, 14));
        member.setGender(Gender.FEMALE);
        member.setAddress("12 Temple Road, Kandy");
        member.setPhoneNumber("0771234567");
        member.setEmail("tharushi@example.com");
        return member;
    }

    @Test
    @DisplayName("An account cannot be created without accepting the privacy notice")
    void refusesRegistrationWithoutPrivacyConsent() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> userService.registerPolicyholder(newMember(), "secret1", false));

        assertTrue(ex.getMessage().contains("Privacy Notice"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Registering records when and which version of the privacy notice was accepted")
    void recordsConsentWhenRegistering() {
        when(userRepository.existsByNic("200012345678")).thenReturn(false);
        when(userRepository.existsByEmail("tharushi@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret1")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User registered = userService.registerPolicyholder(newMember(), "secret1", true);

        assertEquals(Role.POLICYHOLDER, registered.getRole());
        assertNotNull(registered.getPrivacyConsentAt());
        assertEquals(PrivacyNotice.VERSION, registered.getPrivacyNoticeVersion());
        assertTrue(registered.hasCurrentPrivacyConsent());
    }

    @Test
    @DisplayName("An existing member who accepts the notice has their consent recorded and audited")
    void recordsConsentForExistingMember() {
        User member = newMember();
        member.setId(5L);
        member.setRole(Role.POLICYHOLDER);
        assertFalse(member.hasCurrentPrivacyConsent(), "a legacy account starts without consent");

        when(userRepository.findById(5L)).thenReturn(Optional.of(member));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User updated = userService.recordPrivacyConsent(5L, member);

        assertTrue(updated.hasCurrentPrivacyConsent());
        verify(auditService).log(eq("User"), eq(5L), eq("PRIVACY_CONSENT"), eq(member), any());
    }
}
