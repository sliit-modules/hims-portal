package com.medisure.hims.service;

import com.medisure.hims.model.*;
import com.medisure.hims.repository.NotificationRepository;
import com.medisure.hims.repository.PolicyRepository;
import com.medisure.hims.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PolicyRepository policyRepository;

    @InjectMocks
    private NotificationService notificationService;

    private User member;
    private Policy policy;

    @BeforeEach
    void setUp() {
        member = new User();
        member.setId(8L);
        member.setFullName("Kasun Perera");
        member.setRole(Role.POLICYHOLDER);

        policy = new Policy();
        policy.setId(2L);
        policy.setPolicyCode("POL-2025-000002");
        policy.setPolicyholder(member);
        policy.setPremiumAmount(new BigDecimal("8925.00"));
        policy.setStatus(PolicyStatus.ACTIVE);
    }

    @Test
    @DisplayName("Notifying a role reaches every enabled user with that role")
    void notifiesEveryEnabledUserInARole() {
        User officerA = new User();
        officerA.setId(3L);
        User officerB = new User();
        officerB.setId(4L);
        officerB.setEnabled(false);
        when(userRepository.findByRole(Role.CLAIMS_OFFICER)).thenReturn(List.of(officerA, officerB));

        notificationService.notifyRole(Role.CLAIMS_OFFICER, "New claim to review", "details", "/claims/1");

        verify(notificationRepository, times(1)).save(argThat(n -> n.getRecipient() == officerA));
    }

    @Test
    @DisplayName("Opening a notification marks it read, and nobody can open someone else's")
    void opensOnlyYourOwnNotifications() {
        Notification notification = new Notification();
        notification.setId(1L);
        notification.setRecipient(member);
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));

        User someoneElse = new User();
        someoneElse.setId(99L);
        assertThrows(AccessDeniedException.class, () -> notificationService.open(1L, someoneElse));
        assertTrue(notification.isUnread());

        notificationService.open(1L, member);
        assertFalse(notification.isUnread());
    }

    @Test
    @DisplayName("A premium due within 7 days is reminded once, however often the job runs")
    void remindsOncePerDueDate() {
        LocalDate today = LocalDate.of(2026, 9, 21);
        policy.setNextDueDate(today.plusDays(3));
        when(policyRepository.findByStatusIn(any())).thenReturn(List.of(policy));
        when(notificationRepository.existsByRecipientAndReference(member, "PREMIUM_DUE:POL-2025-000002:2026-09-24"))
                .thenReturn(false, true);

        assertEquals(1, notificationService.sendPremiumReminders(today));
        assertEquals(0, notificationService.sendPremiumReminders(today));

        verify(notificationRepository, times(1)).save(argThat(n -> n.getTitle().startsWith("Premium due")));
    }

    @Test
    @DisplayName("An overdue premium is reminded as overdue")
    void remindsOverduePremium() {
        LocalDate today = LocalDate.of(2026, 9, 21);
        policy.setNextDueDate(today.minusDays(5));
        when(policyRepository.findByStatusIn(any())).thenReturn(List.of(policy));

        notificationService.sendPremiumReminders(today);

        verify(notificationRepository).save(argThat(n -> n.getTitle().startsWith("Premium overdue")
                && "/payments/new?policyId=2".equals(n.getLink())));
    }

    @Test
    @DisplayName("No reminder is sent while the due date is more than 7 days away")
    void noReminderWhenNotDueYet() {
        LocalDate today = LocalDate.of(2026, 9, 21);
        policy.setNextDueDate(today.plusDays(30));
        when(policyRepository.findByStatusIn(any())).thenReturn(List.of(policy));

        assertEquals(0, notificationService.sendPremiumReminders(today));
        verify(notificationRepository, never()).save(any(Notification.class));
    }
}
