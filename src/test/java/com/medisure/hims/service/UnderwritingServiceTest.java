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

    @Mock
    private NotificationService notificationService;

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

        // Age 35, no conditions, no dependents, no height/weight: suggested score 10 + 10 = 20.
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
    @DisplayName("Approving records the risk score, the suggestion, the loading and the decision time")
    void approvesWithRiskScoreAndLoading() {
        when(applicationRepository.findById(1L)).thenReturn(Optional.of(application));
        when(applicationRepository.save(any(UnderwritingApplication.class))).thenReturn(application);

        UnderwritingApplication approved = underwritingService.decide(
                1L, ApplicationDecision.APPROVED, 22, new BigDecimal("10.00"), null, testUnderwriter);

        assertEquals(ApplicationDecision.APPROVED, approved.getDecision());
        assertEquals(22, approved.getRiskScore());
        assertEquals(20, approved.getSuggestedRiskScore());
        assertEquals(0, new BigDecimal("10.00").compareTo(approved.getPremiumLoadingPercent()));
        assertNotNull(approved.getDecidedAt(), "a decision must be timestamped");
        verify(auditService).log(eq("UnderwritingApplication"), eq(1L),
                eq("DECIDED:APPROVED"), eq(testUnderwriter), any());
    }

    @Test
    @DisplayName("A high-risk application can be declined, with the reason for the score recorded")
    void rejectsHighRiskApplication() {
        when(applicationRepository.findById(1L)).thenReturn(Optional.of(application));
        when(applicationRepository.save(any(UnderwritingApplication.class))).thenReturn(application);

        UnderwritingApplication rejected = underwritingService.decide(1L, ApplicationDecision.REJECTED, 88,
                BigDecimal.ZERO, "Medical report shows uncontrolled diabetes", testUnderwriter);

        assertEquals(ApplicationDecision.REJECTED, rejected.getDecision());
        assertEquals(88, rejected.getRiskScore());
        assertEquals("Medical report shows uncontrolled diabetes", rejected.getRiskOverrideReason());
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
                1L, ApplicationDecision.APPROVED, 20, BigDecimal.ZERO, null, salesAgent));

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

    @Test
    @DisplayName("An application that has already been decided cannot be decided again")
    void refusesSecondDecision() {
        application.setDecision(ApplicationDecision.APPROVED);
        when(applicationRepository.findById(1L)).thenReturn(Optional.of(application));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> underwritingService.decide(
                1L, ApplicationDecision.REJECTED, 20, BigDecimal.ZERO, null, testUnderwriter));

        assertTrue(ex.getMessage().contains("already been decided"));
        verify(applicationRepository, never()).save(any(UnderwritingApplication.class));
    }

    @Test
    @DisplayName("A missing risk score or one outside 0-100 is refused with a clear message")
    void refusesInvalidRiskScore() {
        when(applicationRepository.findById(1L)).thenReturn(Optional.of(application));

        assertThrows(IllegalStateException.class, () -> underwritingService.decide(
                1L, ApplicationDecision.APPROVED, 150, BigDecimal.ZERO, null, testUnderwriter));
        assertThrows(IllegalStateException.class, () -> underwritingService.decide(
                1L, ApplicationDecision.APPROVED, null, BigDecimal.ZERO, null, testUnderwriter));
        verify(applicationRepository, never()).save(any(UnderwritingApplication.class));
    }

    @Test
    @DisplayName("A negative premium loading is refused")
    void refusesNegativeLoading() {
        when(applicationRepository.findById(1L)).thenReturn(Optional.of(application));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> underwritingService.decide(
                1L, ApplicationDecision.APPROVED, 20, new BigDecimal("-5"), null, testUnderwriter));

        assertTrue(ex.getMessage().contains("zero or more"));
        verify(applicationRepository, never()).save(any(UnderwritingApplication.class));
    }

    // ---------------- suggested risk score (PBI29) ----------------

    @Test
    @DisplayName("A young applicant with no risk factors gets the base score and no loading")
    void suggestsLowScoreForLowRisk() {
        application.setApplicantAge(25);

        RiskAssessment risk = underwritingService.assessRisk(application);

        assertEquals(10, risk.getScore());
        assertEquals(0, BigDecimal.ZERO.compareTo(risk.getSuggestedLoadingPercent()));
        assertFalse(risk.isHighRisk());
        assertEquals("Base score", risk.getFactors().get(0).getLabel());
    }

    @Test
    @DisplayName("Age, conditions, dependents and BMI each add points, and a high total is flagged")
    void suggestsHighScoreForSeveralFactors() {
        application.setApplicantAge(50);                  // +25
        application.setHasPreExistingConditions(true);    // +25
        application.setNumDependentsPlanned(2);           // +10
        applicant.setHeightCm(170);                       // BMI 32.9: +10
        applicant.setWeightKg(95);

        RiskAssessment risk = underwritingService.assessRisk(application);

        assertEquals(80, risk.getScore());                // 10 base + 25 + 25 + 10 + 10
        assertTrue(risk.isHighRisk());
        assertEquals(0, new BigDecimal("50").compareTo(risk.getSuggestedLoadingPercent()));
    }

    @Test
    @DisplayName("A score far from the suggestion is refused without a reason")
    void requiresReasonForLargeOverride() {
        when(applicationRepository.findById(1L)).thenReturn(Optional.of(application));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> underwritingService.decide(
                1L, ApplicationDecision.APPROVED, 60, BigDecimal.TEN, " ", testUnderwriter));

        assertTrue(ex.getMessage().contains("please give a reason"));
        verify(applicationRepository, never()).save(any(UnderwritingApplication.class));
    }

    @Test
    @DisplayName("A score within 10 points of the suggestion needs no reason")
    void acceptsSmallDifferenceWithoutReason() {
        when(applicationRepository.findById(1L)).thenReturn(Optional.of(application));
        when(applicationRepository.save(any(UnderwritingApplication.class))).thenReturn(application);

        UnderwritingApplication decided = underwritingService.decide(
                1L, ApplicationDecision.APPROVED, 30, BigDecimal.TEN, null, testUnderwriter);

        assertEquals(30, decided.getRiskScore());
        assertNull(decided.getRiskOverrideReason());
    }

    // ---------------- withdrawing an application (PBI29) ----------------

    @Test
    @DisplayName("An applicant can withdraw their own pending application")
    void applicantWithdrawsPendingApplication() {
        when(applicationRepository.findById(1L)).thenReturn(Optional.of(application));
        when(applicationRepository.save(any(UnderwritingApplication.class))).thenAnswer(inv -> inv.getArgument(0));

        UnderwritingApplication withdrawn = underwritingService.withdraw(1L, applicant);

        assertEquals(ApplicationDecision.WITHDRAWN, withdrawn.getDecision());
        verify(auditService).log(eq("UnderwritingApplication"), eq(1L), eq("WITHDRAWN"), eq(applicant), any());
    }

    @Test
    @DisplayName("A decided application can no longer be withdrawn")
    void refusesWithdrawalOfDecidedApplication() {
        application.setDecision(ApplicationDecision.APPROVED);
        when(applicationRepository.findById(1L)).thenReturn(Optional.of(application));

        assertThrows(IllegalStateException.class, () -> underwritingService.withdraw(1L, applicant));
        verify(applicationRepository, never()).save(any(UnderwritingApplication.class));
    }

    @Test
    @DisplayName("Nobody else can withdraw a member's application")
    void refusesWithdrawalByAnotherMember() {
        User otherMember = new User();
        otherMember.setId(99L);
        otherMember.setRole(Role.POLICYHOLDER);
        when(applicationRepository.findById(1L)).thenReturn(Optional.of(application));

        assertThrows(AccessDeniedException.class, () -> underwritingService.withdraw(1L, otherMember));
        verify(applicationRepository, never()).save(any(UnderwritingApplication.class));
    }
}
