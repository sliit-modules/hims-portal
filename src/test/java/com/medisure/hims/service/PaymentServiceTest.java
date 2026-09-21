package com.medisure.hims.service;

import com.medisure.hims.model.*;
import com.medisure.hims.repository.PaymentRepository;
import com.medisure.hims.repository.PolicyRepository;
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
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PolicyRepository policyRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private PaymentService paymentService;

    private Policy policy;
    private Payment testPayment;
    private User policyholder;
    private User claimsOfficer;
    private User admin;

    @BeforeEach
    void setUp() {
        policyholder = new User();
        policyholder.setId(40L);
        policyholder.setNic("199009098765");
        policyholder.setFullName("Kasun Perera");
        policyholder.setRole(Role.POLICYHOLDER);

        claimsOfficer = new User();
        claimsOfficer.setId(50L);
        claimsOfficer.setFullName("Gunasinghe N.M.");
        claimsOfficer.setRole(Role.CLAIMS_OFFICER);

        admin = new User();
        admin.setId(60L);
        admin.setFullName("Karunarathna W.M.K.U.");
        admin.setRole(Role.ADMIN);

        policy = new Policy();
        policy.setId(1L);
        policy.setPolicyCode("POL-2026-000001");
        policy.setPolicyholder(policyholder);
        policy.setPremiumAmount(new BigDecimal("15000.00"));
        policy.setPremiumFrequency(PremiumFrequency.MONTHLY);
        policy.setNextDueDate(LocalDate.now());

        testPayment = new Payment();
        testPayment.setId(1L);
        testPayment.setPolicy(policy);
        testPayment.setAmount(new BigDecimal("15000.00"));
        testPayment.setMethod(PaymentMethod.CARD);
        testPayment.setStatus(PaymentStatus.PAID);
        testPayment.setPaidAt(LocalDateTime.now());
    }

    @Test
    @DisplayName("Paying a premium records the billing period and advances the next due date")
    void recordsPremiumPayment() {
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        LocalDate dueBefore = policy.getNextDueDate();
        Payment payment = paymentService.makePayment(policy, new BigDecimal("15000.00"),
                PaymentMethod.CARD, true, policyholder);

        assertEquals(PaymentStatus.PAID, payment.getStatus());
        assertEquals(dueBefore, payment.getBillingPeriodStart());
        assertEquals(dueBefore.plusMonths(1), payment.getBillingPeriodEnd());
        assertEquals(dueBefore.plusMonths(1), policy.getNextDueDate(), "next due date should roll forward");
        verify(policyRepository).save(policy);
        verify(auditService).log(eq("Payment"), any(), eq("PAID"), eq(policyholder), any());
    }

    @Test
    @DisplayName("A quarterly policy advances the due date by three months")
    void honoursQuarterlyFrequency() {
        policy.setPremiumFrequency(PremiumFrequency.QUARTERLY);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        LocalDate dueBefore = policy.getNextDueDate();
        Payment payment = paymentService.makePayment(policy, new BigDecimal("45000.00"),
                PaymentMethod.BANK_TRANSFER, false, policyholder);

        assertEquals(dueBefore.plusMonths(3), payment.getBillingPeriodEnd());
    }

    @Test
    @DisplayName("Changing the payment method and auto-pay flag is recorded")
    void updatesPaymentMethod() {
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(testPayment));
        when(paymentRepository.save(any(Payment.class))).thenReturn(testPayment);

        Payment updated = paymentService.updateMethod(1L, PaymentMethod.ONLINE_WALLET, true, policyholder);

        assertEquals(PaymentMethod.ONLINE_WALLET, updated.getMethod());
        assertTrue(updated.isAutoPay());
        verify(auditService).log(eq("Payment"), eq(1L), eq("METHOD_UPDATED"), eq(policyholder), any());
    }

    // ---------------- refund requests (PBI25) ----------------

    @Test
    @DisplayName("A member's refund request waits for approval; the payment stays PAID")
    void requestsRefundOnOwnPayment() {
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(testPayment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Payment requested = paymentService.requestRefund(1L, "Charged twice this month", policyholder);

        assertTrue(requested.isRefundPending());
        assertEquals(PaymentStatus.PAID, requested.getStatus(), "no money moves until staff approve");
        assertEquals("Charged twice this month", requested.getRefundReason());
        verify(auditService).log(eq("Payment"), eq(1L), eq("REFUND_REQUESTED"), eq(policyholder), any());
    }

    @Test
    @DisplayName("A refund request needs a reason")
    void refusesRefundRequestWithoutReason() {
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(testPayment));

        assertThrows(IllegalStateException.class, () -> paymentService.requestRefund(1L, " ", policyholder));

        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("A second refund request cannot be made while one is pending")
    void refusesSecondRefundRequestWhilePending() {
        testPayment.setRefundRequestedAt(LocalDateTime.now());
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(testPayment));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> paymentService.requestRefund(1L, "Again", policyholder));

        assertTrue(ex.getMessage().contains("already been requested"));
    }

    @Test
    @DisplayName("A member cannot request a refund on another member's payment")
    void refusesRefundRequestFromAnotherMember() {
        User otherMember = new User();
        otherMember.setId(99L);
        otherMember.setRole(Role.POLICYHOLDER);
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(testPayment));

        assertThrows(AccessDeniedException.class,
                () -> paymentService.requestRefund(1L, "Not mine", otherMember));

        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("A claims officer can approve a pending refund, which marks the payment REFUNDED")
    void claimsOfficerApprovesRefund() {
        testPayment.setRefundRequestedAt(LocalDateTime.now());
        testPayment.setRefundReason("Charged twice");
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(testPayment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Payment decided = paymentService.decideRefund(1L, true, "Duplicate confirmed", claimsOfficer);

        assertEquals(PaymentStatus.REFUNDED, decided.getStatus());
        assertFalse(decided.isRefundPending());
        assertEquals(claimsOfficer, decided.getRefundDecidedBy());
        verify(auditService).log(eq("Payment"), eq(1L), eq("REFUND_APPROVED"), eq(claimsOfficer), any());
    }

    @Test
    @DisplayName("An admin can reject a refund with notes; the payment stays PAID")
    void adminRejectsRefundWithNotes() {
        testPayment.setRefundRequestedAt(LocalDateTime.now());
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(testPayment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Payment decided = paymentService.decideRefund(1L, false, "Only one charge found on the statement", admin);

        assertEquals(PaymentStatus.PAID, decided.getStatus());
        assertEquals("Only one charge found on the statement", decided.getRefundDecisionNotes());
        verify(auditService).log(eq("Payment"), eq(1L), eq("REFUND_REJECTED"), eq(admin), any());
    }

    @Test
    @DisplayName("Rejecting a refund requires notes")
    void refusesRejectionWithoutNotes() {
        testPayment.setRefundRequestedAt(LocalDateTime.now());
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(testPayment));

        assertThrows(IllegalStateException.class, () -> paymentService.decideRefund(1L, false, "", admin));

        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("A policyholder cannot approve their own refund")
    void refusesRefundDecisionFromPolicyholder() {
        assertThrows(AccessDeniedException.class,
                () -> paymentService.decideRefund(1L, true, "Self approval", policyholder));

        verify(paymentRepository, never()).save(any(Payment.class));
    }

    // ---------------- voiding (replaces member self-cancel) ----------------

    @Test
    @DisplayName("Staff can void a payment recorded in error, with a reason")
    void staffVoidsPaymentRecordedInError() {
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(testPayment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Payment voided = paymentService.voidPayment(1L, "Duplicate entry", admin);

        assertEquals(PaymentStatus.CANCELLED, voided.getStatus());
        assertEquals("Duplicate entry", voided.getVoidReason());
        verify(auditService).log(eq("Payment"), eq(1L), eq("VOIDED"), eq(admin), any());
    }

    @Test
    @DisplayName("A policyholder cannot void a payment")
    void refusesVoidFromPolicyholder() {
        assertThrows(AccessDeniedException.class,
                () -> paymentService.voidPayment(1L, "Changed my mind", policyholder));

        verify(paymentRepository, never()).save(any(Payment.class));
    }
}
