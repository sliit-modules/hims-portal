package com.medisure.hims.service;

import com.medisure.hims.model.*;
import com.medisure.hims.repository.PolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PolicyServiceTest {

    @Mock
    private PolicyRepository policyRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private PolicyService policyService;

    private Policy testPolicy;
    private User testAgent;

    @BeforeEach
    void setUp() {
        testPolicy = new Policy();
        testPolicy.setId(1L);
        testPolicy.setPolicyNumber("POL-2026-000001");
        testPolicy.setStatus(PolicyStatus.ACTIVE);
        testPolicy.setStartDate(LocalDate.now());
        testPolicy.setEndDate(LocalDate.now().plusYears(1));
        testPolicy.setTotalPremium(new BigDecimal("60000.00"));

        testAgent = new User();
        testAgent.setId(30L);
        testAgent.setUsername("sales_agent");
    }

    @Test
    void testCancelPolicy() {
        when(policyRepository.findById(1L)).thenReturn(Optional.of(testPolicy));
        when(policyRepository.save(any(Policy.class))).thenReturn(testPolicy);

        policyService.cancel(1L, "Customer relocation abroad", testAgent);
        assertEquals(PolicyStatus.CANCELLED, testPolicy.getStatus());
        verify(auditService).log(eq("Policy"), eq(1L), eq("CANCELLED"), eq(testAgent), any());
    }

    @Test
    void testRenewPolicy() {
        when(policyRepository.findById(1L)).thenReturn(Optional.of(testPolicy));
        when(policyRepository.save(any(Policy.class))).thenReturn(testPolicy);

        LocalDate oldEnd = testPolicy.getEndDate();
        policyService.renew(1L, testAgent);
        assertEquals(oldEnd.plusYears(1), testPolicy.getEndDate());
        verify(auditService).log(eq("Policy"), eq(1L), eq("RENEWED"), eq(testAgent), any());
    }
}
