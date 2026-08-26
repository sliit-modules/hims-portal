package com.medisure.hims.service;

import com.medisure.hims.model.*;
import com.medisure.hims.repository.UnderwritingApplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UnderwritingServiceTest {

    @Mock
    private UnderwritingApplicationRepository applicationRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private UnderwritingService underwritingService;

    private UnderwritingApplication application;
    private User testUnderwriter;

    @BeforeEach
    void setUp() {
        application = new UnderwritingApplication();
        application.setId(1L);
        application.setFullName("John Perera");
        application.setAge(35);
        application.setSmoker(false);
        application.setDecision(ApplicationDecision.PENDING);

        testUnderwriter = new User();
        testUnderwriter.setId(20L);
        testUnderwriter.setUsername("underwriter");
    }

    @Test
    void testApproveDecision() {
        when(applicationRepository.findById(1L)).thenReturn(Optional.of(application));
        when(applicationRepository.save(any(UnderwritingApplication.class))).thenReturn(application);

        UnderwritingApplication approved = underwritingService.decide(1L, ApplicationDecision.APPROVED, new BigDecimal("10.00"), "Low risk profile", testUnderwriter);
        assertNotNull(approved);
        assertEquals(ApplicationDecision.APPROVED, approved.getDecision());
        verify(auditService).log(eq("UnderwritingApplication"), eq(1L), eq("DECIDED"), eq(testUnderwriter), any());
    }

    @Test
    void testRejectDecision() {
        when(applicationRepository.findById(1L)).thenReturn(Optional.of(application));
        when(applicationRepository.save(any(UnderwritingApplication.class))).thenReturn(application);

        UnderwritingApplication rejected = underwritingService.decide(1L, ApplicationDecision.REJECTED, BigDecimal.ZERO, "High risk medical conditions", testUnderwriter);
        assertNotNull(rejected);
        assertEquals(ApplicationDecision.REJECTED, rejected.getDecision());
    }
}
