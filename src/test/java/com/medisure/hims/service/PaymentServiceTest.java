package com.medisure.hims.service;

import com.medisure.hims.model.*;
import com.medisure.hims.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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
    private AuditService auditService;

    @InjectMocks
    private PaymentService paymentService;

    private Payment testPayment;
    private User testUser;

    @BeforeEach
    void setUp() {
        testPayment = new Payment();
        testPayment.setId(1L);
        testPayment.setReceiptNumber("REC-2026-000001");
        testPayment.setAmount(new BigDecimal("15000.00"));
        testPayment.setStatus(PaymentStatus.PAID);
        testPayment.setPaymentDate(LocalDateTime.now());

        testUser = new User();
        testUser.setId(40L);
        testUser.setUsername("policyholder");
    }

    @Test
    void testRefundPayment() {
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(testPayment));
        when(paymentRepository.save(any(Payment.class))).thenReturn(testPayment);

        paymentService.refund(1L, "Double charged by card gateway", testUser);
        assertEquals(PaymentStatus.REFUNDED, testPayment.getStatus());
        verify(auditService).log(eq("Payment"), eq(1L), eq("REFUNDED"), eq(testUser), any());
    }
}
