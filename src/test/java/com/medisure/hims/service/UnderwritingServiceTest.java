package com.medisure.hims.service;

import com.medisure.hims.model.*;
import com.medisure.hims.repository.UnderwritingApplicationRepository;
import com.medisure.hims.util.CodeGenerator;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UnderwritingServiceTest {

    @Mock
    private UnderwritingApplicationRepository applicationRepository;

    @Mock
    private CodeGenerator codeGenerator;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private UnderwritingService underwritingService;

    private InsurancePlan plan;
    private UnderwritingApplication application;
    private User applicant;
    private User testUnderwriter;

    @BeforeEach
    void setUp() {
        plan = new InsurancePlan();
        plan.setId(1L);
        plan.setPlanName("MediSure Critical Illness Plus");
        plan.setPlanType(PlanType.INDIVIDUAL);
        plan.setPremiumRate(new BigDecimal("6800.00"));
        plan.setCoverageLimit(new BigDecimal("5000000.00"));

        applicant = new User();
        applicant.setId(41L);
        applicant.setNic("199009098765");
        applicant.setFullName("John Perera");
        applicant.setDateOfBirth(LocalDate.now().minusYears(35));
        applicant.setRole(Role.POLICYHOLDER);

        application = new UnderwritingApplication();
        application.setId(1L);
        application.setApplicationCode("APP-2026-000001");
        application.setApplicant(applicant);
        application.setRequestedPlan(plan);
        application.setApplicantAge(35);
        application.setHasPreExistingConditions(false);
        application.setDecision(ApplicationDecision.PENDING);

        testUnderwriter = new User();
        testUnderwriter.setId(20L);
        testUnderwriter.setNic("197505045678");
        testUnderwriter.setFullName("De Zoysa A.I.");
        testUnderwriter.setRole(Role.UNDERWRITER);
    }

    @Test
    @DisplayName("Approving records the risk score, premium loading and decision time")
    void approvesWithRiskScoreAndLoading() {
        when(applicationRepository.findById(1L)).thenReturn(Optional.of(application));
        when(applicationRepository.save(any(UnderwritingApplication.class))).thenReturn(application);

        UnderwritingApplication approved = underwritingService.decide(
                1L, ApplicationDecision.APPROVED, 22, new BigDecimal("10.00"), testUnderwriter);

        assertEquals(ApplicationDecision.APPROVED, approved.getDecision());
        assertEquals(22, approved.getRiskScore());
        assertEquals(0, new BigDecimal("10.00").compareTo(approved.getPremiumLoadingPercent()));
        assertNotNull(approved.getDecidedAt(), "a decision must be timestamped");
        verify(auditService).log(eq("UnderwritingApplication"), eq(1L),
                eq("DECIDED:APPROVED"), eq(testUnderwriter), any());
    }

    @Test
    @DisplayName("A high-risk application can be declined")
    void rejectsHighRiskApplication() {
        when(applicationRepository.findById(1L)).thenReturn(Optional.of(application));
        when(applicationRepository.save(any(UnderwritingApplication.class))).thenReturn(application);

        UnderwritingApplication rejected = underwritingService.decide(
                1L, ApplicationDecision.REJECTED, 88, BigDecimal.ZERO, testUnderwriter);

        assertEquals(ApplicationDecision.REJECTED, rejected.getDecision());
        assertEquals(88, rejected.getRiskScore());
        verify(auditService).log(eq("UnderwritingApplication"), eq(1L),
                eq("DECIDED:REJECTED"), eq(testUnderwriter), any());
    }

    @Test
    @DisplayName("Only an underwriter or admin may decide an application")
    void refusesDecisionFromUnauthorisedRole() {
        User salesAgent = new User();
        salesAgent.setId(30L);
        salesAgent.setFullName("Lankadhikara L.R.M.M.P.");
        salesAgent.setRole(Role.SALES_AGENT);

        assertThrows(AccessDeniedException.class, () -> underwritingService.decide(
                1L, ApplicationDecision.APPROVED, 20, BigDecimal.ZERO, salesAgent));

        verify(applicationRepository, never()).save(any(UnderwritingApplication.class));
    }

    @Test
    @DisplayName("A submitted application starts pending with the applicant's age derived from their date of birth")
    void submitsApplicationAsPending() {
        when(codeGenerator.nextApplicationCode()).thenReturn("APP-2026-000002");
        when(applicationRepository.save(any(UnderwritingApplication.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        UnderwritingApplication submitted = underwritingService.submit(
                applicant, plan, true, "Declared hypertension", 2, applicant);

        assertEquals(ApplicationDecision.PENDING, submitted.getDecision());
        assertEquals("APP-2026-000002", submitted.getApplicationCode());
        assertEquals(35, submitted.getApplicantAge());
        assertTrue(submitted.isHasPreExistingConditions());
        assertEquals(2, submitted.getNumDependentsPlanned());
        verify(auditService).log(eq("UnderwritingApplication"), any(), eq("SUBMITTED"), eq(applicant), any());
    }
}
