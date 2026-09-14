package com.medisure.hims.service;

import com.medisure.hims.model.*;
import com.medisure.hims.repository.DependentRepository;
import com.medisure.hims.repository.PolicyRepository;
import com.medisure.hims.util.CodeGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
    private DependentRepository dependentRepository;

    @Mock
    private CodeGenerator codeGenerator;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private PolicyService policyService;

    private InsurancePlan familyPlan;
    private Policy testPolicy;
    private User testAgent;

    @BeforeEach
    void setUp() {
        familyPlan = new InsurancePlan();
        familyPlan.setId(1L);
        familyPlan.setPlanName("MediSure Family Shield");
        familyPlan.setPlanType(PlanType.FAMILY);
        familyPlan.setPremiumRate(new BigDecimal("8500.00"));
        familyPlan.setCoverageLimit(new BigDecimal("2000000.00"));

        testPolicy = new Policy();
        testPolicy.setId(1L);
        testPolicy.setPolicyCode("POL-2026-000001");
        testPolicy.setPlan(familyPlan);
        testPolicy.setStatus(PolicyStatus.ACTIVE);
        testPolicy.setStartDate(LocalDate.now());
        testPolicy.setEndDate(LocalDate.now().plusYears(1));
        testPolicy.setPremiumAmount(new BigDecimal("8925.00"));
        testPolicy.setPremiumFrequency(PremiumFrequency.MONTHLY);

        testAgent = new User();
        testAgent.setId(30L);
        testAgent.setNic("199203023456");
        testAgent.setFullName("Lankadhikara L.R.M.M.P.");
        testAgent.setRole(Role.SALES_AGENT);
    }

    @Test
    @DisplayName("Cancelling a policy marks it CANCELLED and writes an audit entry")
    void cancelsPolicy() {
        when(policyRepository.findById(1L)).thenReturn(Optional.of(testPolicy));

        policyService.cancel(1L, testAgent);

        assertEquals(PolicyStatus.CANCELLED, testPolicy.getStatus());
        verify(policyRepository).save(testPolicy);
        verify(auditService).log(eq("Policy"), eq(1L), eq("CANCELLED"), eq(testAgent), any());
    }

    @Test
    @DisplayName("Renewing extends the cover period and records the new frequency")
    void renewsPolicy() {
        when(policyRepository.findById(1L)).thenReturn(Optional.of(testPolicy));
        when(policyRepository.save(any(Policy.class))).thenReturn(testPolicy);

        LocalDate newEnd = testPolicy.getEndDate().plusYears(1);
        Policy renewed = policyService.renewOrModify(1L, newEnd, PremiumFrequency.QUARTERLY, testAgent);

        assertEquals(newEnd, renewed.getEndDate());
        assertEquals(PremiumFrequency.QUARTERLY, renewed.getPremiumFrequency());
        assertEquals(PolicyStatus.RENEWED, renewed.getStatus());
        verify(auditService).log(eq("Policy"), eq(1L), eq("RENEWED"), eq(testAgent), any());
    }

    @Test
    @DisplayName("A policy is only issued from an approved underwriting application")
    void refusesIssueFromPendingApplication() {
        UnderwritingApplication pending = new UnderwritingApplication();
        pending.setId(9L);
        pending.setRequestedPlan(familyPlan);
        pending.setDecision(ApplicationDecision.PENDING);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> policyService.issueFromApplication(pending, PremiumFrequency.MONTHLY, testAgent));

        assertTrue(ex.getMessage().contains("approved"));
        verify(policyRepository, never()).save(any(Policy.class));
    }

    @Test
    @DisplayName("Issuing applies the underwriting loading to the plan's base premium")
    void appliesPremiumLoadingOnIssue() {
        User applicant = new User();
        applicant.setId(41L);
        applicant.setFullName("Kasun Perera");
        applicant.setRole(Role.POLICYHOLDER);

        UnderwritingApplication approved = new UnderwritingApplication();
        approved.setId(10L);
        approved.setApplicant(applicant);
        approved.setRequestedPlan(familyPlan);
        approved.setDecision(ApplicationDecision.APPROVED);
        approved.setPremiumLoadingPercent(new BigDecimal("5.00"));

        when(policyRepository.findBySourceApplicationId(10L)).thenReturn(Optional.empty());
        when(codeGenerator.nextPolicyCode()).thenReturn("POL-2026-000002");
        when(policyRepository.save(any(Policy.class))).thenAnswer(inv -> inv.getArgument(0));

        Policy issued = policyService.issueFromApplication(approved, PremiumFrequency.MONTHLY, testAgent);

        // 8500 base + 5% loading = 8925
        assertEquals(0, new BigDecimal("8925.00").compareTo(issued.getPremiumAmount()));
        assertEquals(PolicyStatus.ACTIVE, issued.getStatus());
        assertEquals("POL-2026-000002", issued.getPolicyCode());
    }

    @Test
    @DisplayName("The same application cannot be issued as a policy twice")
    void refusesDuplicateIssue() {
        UnderwritingApplication approved = new UnderwritingApplication();
        approved.setId(10L);
        approved.setRequestedPlan(familyPlan);
        approved.setDecision(ApplicationDecision.APPROVED);
        approved.setPremiumLoadingPercent(BigDecimal.ZERO);

        when(policyRepository.findBySourceApplicationId(10L)).thenReturn(Optional.of(testPolicy));

        assertThrows(IllegalStateException.class,
                () -> policyService.issueFromApplication(approved, PremiumFrequency.MONTHLY, testAgent));
    }

    @Test
    @DisplayName("A covered member aged 18 or over must have a NIC")
    void refusesAdultDependentWithoutNic() {
        Dependent adult = new Dependent();
        adult.setFullName("Nadeesha Perera");
        adult.setDateOfBirth(LocalDate.now().minusYears(34));
        adult.setRelationship(RelationshipType.SPOUSE);

        when(policyRepository.findById(1L)).thenReturn(Optional.of(testPolicy));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> policyService.addDependent(1L, adult, testAgent));

        assertTrue(ex.getMessage().contains("NIC is required"));
        verify(dependentRepository, never()).save(any(Dependent.class));
    }

    @Test
    @DisplayName("A minor may be covered without a NIC")
    void acceptsMinorDependentWithoutNic() {
        Dependent child = new Dependent();
        child.setFullName("Dinuk Perera");
        child.setDateOfBirth(LocalDate.now().minusYears(9));
        child.setRelationship(RelationshipType.CHILD);

        when(policyRepository.findById(1L)).thenReturn(Optional.of(testPolicy));
        when(dependentRepository.save(any(Dependent.class))).thenAnswer(inv -> inv.getArgument(0));

        Dependent saved = policyService.addDependent(1L, child, testAgent);

        assertEquals(testPolicy, saved.getPolicy());
        assertFalse(saved.isNicRequired());
    }

    @Test
    @DisplayName("Dependents cannot be added to an individual plan")
    void refusesDependentOnIndividualPlan() {
        familyPlan.setPlanType(PlanType.INDIVIDUAL);

        Dependent child = new Dependent();
        child.setFullName("Dinuk Perera");
        child.setDateOfBirth(LocalDate.now().minusYears(9));

        when(policyRepository.findById(1L)).thenReturn(Optional.of(testPolicy));

        assertThrows(IllegalStateException.class, () -> policyService.addDependent(1L, child, testAgent));
    }
}
