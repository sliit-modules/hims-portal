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

    @InjectMocks
    private PaymentService paymentService;

    private Policy policy;
    private Payment testPayment;
    private User policyholder;

    @BeforeEach
    void setUp() {
        policyholder = new User();
        policyholder.setId(40L);
        policyholder.setNic("199009098765");
        policyholder.setFullName("Kasun Perera");
        policyholder.setRole(Role.POLICYHOLDER);

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
    @DisplayName("A member can refund their own payment")
    void refundsOwnPayment() {
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(testPayment));

        paymentService.cancelOrRefund(1L, PaymentStatus.REFUNDED, policyholder);

        assertEquals(PaymentStatus.REFUNDED, testPayment.getStatus());
        verify(paymentRepository).save(testPayment);
        verify(auditService).log(eq("Payment"), eq(1L), eq("REFUNDED"), eq(policyholder), any());
    }

    @Test
    @DisplayName("A member can cancel their own payment")
    void cancelsOwnPayment() {
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(testPayment));

        paymentService.cancelOrRefund(1L, PaymentStatus.CANCELLED, policyholder);

        assertEquals(PaymentStatus.CANCELLED, testPayment.getStatus());
    }

    @Test
    @DisplayName("A member cannot touch another member's payment")
    void refusesAccessToAnotherMembersPayment() {
        User otherMember = new User();
        otherMember.setId(99L);
        otherMember.setFullName("Someone Else");
        otherMember.setRole(Role.POLICYHOLDER);

        when(paymentRepository.findById(1L)).thenReturn(Optional.of(testPayment));

        assertThrows(AccessDeniedException.class,
                () -> paymentService.cancelOrRefund(1L, PaymentStatus.REFUNDED, otherMember));

        verify(paymentRepository, never()).save(any(Payment.class));
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
}
