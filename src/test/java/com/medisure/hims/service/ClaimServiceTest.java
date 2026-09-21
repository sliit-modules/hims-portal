package com.medisure.hims.service;

import com.medisure.hims.model.*;
import com.medisure.hims.repository.ClaimRepository;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimServiceTest {

    @Mock
    private ClaimRepository claimRepository;

    @Mock
    private CodeGenerator codeGenerator;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private ClaimService claimService;

    private InsurancePlan plan;
    private Policy policy;
    private Claim testClaim;
    private User policyholder;
    private User claimsOfficer;

    @BeforeEach
    void setUp() {
        plan = new InsurancePlan();
        plan.setId(1L);
        plan.setPlanName("MediSure Family Shield");
        plan.setPlanType(PlanType.FAMILY);
        plan.setCoverageLimit(new BigDecimal("2000000.00"));

        policyholder = new User();
        policyholder.setId(40L);
        policyholder.setNic("199009098765");
        policyholder.setFullName("Kasun Perera");
        policyholder.setRole(Role.POLICYHOLDER);

        policy = new Policy();
        policy.setId(1L);
        policy.setPolicyCode("POL-2026-000001");
        policy.setPlan(plan);
        policy.setPolicyholder(policyholder);

        testClaim = new Claim();
        testClaim.setId(1L);
        testClaim.setClaimCode("CLM-2026-000001");
        testClaim.setPolicy(policy);
        testClaim.setClaimant(policyholder);
        testClaim.setAmountClaimed(new BigDecimal("75000.00"));
        testClaim.setStatus(ClaimStatus.SUBMITTED);

        claimsOfficer = new User();
        claimsOfficer.setId(50L);
        claimsOfficer.setNic("198804034567");
        claimsOfficer.setFullName("Gunasinghe N.M.");
        claimsOfficer.setRole(Role.CLAIMS_OFFICER);
    }

    @Test
    @DisplayName("An officer can approve a claim that fits within the remaining cover")
    void approvesClaimWithinCoverage() {
        when(claimRepository.findById(1L)).thenReturn(Optional.of(testClaim));
        when(claimRepository.findByPolicyAndStatus(policy, ClaimStatus.APPROVED)).thenReturn(List.of());
        when(claimRepository.save(any(Claim.class))).thenReturn(testClaim);

        Claim decided = claimService.decide(1L, ClaimStatus.APPROVED,
                "Covered under surgical benefit", claimsOfficer);

        assertEquals(ClaimStatus.APPROVED, decided.getStatus());
        assertEquals("Covered under surgical benefit", decided.getDecisionNotes());
        verify(auditService).log(eq("Claim"), eq(1L), eq("DECIDED:APPROVED"), eq(claimsOfficer), any());
    }

    @Test
    @DisplayName("An officer can reject a claim")
    void rejectsClaim() {
        when(claimRepository.findById(1L)).thenReturn(Optional.of(testClaim));
        when(claimRepository.save(any(Claim.class))).thenReturn(testClaim);

        Claim decided = claimService.decide(1L, ClaimStatus.REJECTED,
                "Exceeds annual outpatient limit", claimsOfficer);

        assertEquals(ClaimStatus.REJECTED, decided.getStatus());
        verify(auditService).log(eq("Claim"), eq(1L), eq("DECIDED:REJECTED"), eq(claimsOfficer), any());
    }

    @Test
    @DisplayName("Approval is blocked when the claim would exceed the policy's remaining cover")
    void refusesApprovalBeyondRemainingCoverage() {
        Claim alreadyApproved = new Claim();
        alreadyApproved.setAmountClaimed(new BigDecimal("1960000.00"));
        alreadyApproved.setStatus(ClaimStatus.APPROVED);

        when(claimRepository.findById(1L)).thenReturn(Optional.of(testClaim));
        when(claimRepository.findByPolicyAndStatus(policy, ClaimStatus.APPROVED))
                .thenReturn(List.of(alreadyApproved));

        // 2,000,000 limit - 1,960,000 already approved = 40,000 left, but this claim is 75,000.
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> claimService.decide(1L, ClaimStatus.APPROVED, "Approve anyway", claimsOfficer));

        assertTrue(ex.getMessage().contains("remaining coverage"));
        assertEquals(ClaimStatus.SUBMITTED, testClaim.getStatus(), "claim must stay pending");
        verify(claimRepository, never()).save(any(Claim.class));
    }

    @Test
    @DisplayName("Only a claims officer or admin may decide a claim")
    void refusesDecisionFromUnauthorisedRole() {
        assertThrows(AccessDeniedException.class,
                () -> claimService.decide(1L, ClaimStatus.APPROVED, "Self approval", policyholder));

        verify(claimRepository, never()).save(any(Claim.class));
    }

    @Test
    @DisplayName("A member may withdraw their own claim while it is still pending")
    void withdrawsOwnPendingClaim() {
        when(claimRepository.findById(1L)).thenReturn(Optional.of(testClaim));

        claimService.withdraw(1L, policyholder);

        assertEquals(ClaimStatus.WITHDRAWN, testClaim.getStatus());
        verify(auditService).log(eq("Claim"), eq(1L), eq("WITHDRAWN"), eq(policyholder), any());
    }

    @Test
    @DisplayName("A claim that has already been decided can no longer be withdrawn")
    void refusesWithdrawalOfDecidedClaim() {
        testClaim.setStatus(ClaimStatus.APPROVED);
        when(claimRepository.findById(1L)).thenReturn(Optional.of(testClaim));

        assertThrows(IllegalStateException.class, () -> claimService.withdraw(1L, policyholder));
    }

    @Test
    @DisplayName("Remaining cover is the plan limit minus everything already approved")
    void calculatesRemainingCoverage() {
        Claim settled = new Claim();
        settled.setAmountClaimed(new BigDecimal("150000.00"));
        settled.setStatus(ClaimStatus.APPROVED);

        when(claimRepository.findByPolicyAndStatus(policy, ClaimStatus.APPROVED))
                .thenReturn(List.of(settled));

        assertEquals(0, new BigDecimal("1850000.00").compareTo(claimService.remainingCoverage(policy)));
    }

    @Test
    @DisplayName("A member can file a claim within the remaining cover on an active policy")
    void submitsClaimWithinCoverage() {
        when(claimRepository.findByPolicyAndStatus(policy, ClaimStatus.APPROVED)).thenReturn(List.of());
        when(codeGenerator.nextClaimCode()).thenReturn("CLM-2026-000002");
        when(claimRepository.save(any(Claim.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Claim claim = claimService.submit(policy, policyholder, null, ClaimCategory.SURGERY, "Asiri Surgical",
                LocalDate.now().minusDays(3), "Appendectomy", new BigDecimal("120000.00"), policyholder);

        assertEquals(ClaimStatus.SUBMITTED, claim.getStatus());
        assertEquals("CLM-2026-000002", claim.getClaimCode());
        verify(auditService).log(eq("Claim"), any(), eq("SUBMITTED"), eq(policyholder), eq("CLM-2026-000002"));
    }

    @Test
    @DisplayName("Claims cannot be filed against a cancelled policy")
    void refusesClaimOnCancelledPolicy() {
        policy.setStatus(PolicyStatus.CANCELLED);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> claimService.submit(policy, policyholder, null, ClaimCategory.DENTAL, "City Dental",
                        LocalDate.now(), "Filling", new BigDecimal("5000.00"), policyholder));

        assertTrue(ex.getMessage().contains("active policy"));
        verify(claimRepository, never()).save(any(Claim.class));
    }

    @Test
    @DisplayName("A treatment date in the future is refused with a clear message")
    void refusesFutureTreatmentDate() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> claimService.submit(policy, policyholder, null, ClaimCategory.EMERGENCY, "Nawaloka Hospital",
                        LocalDate.now().plusDays(1), "Fracture", new BigDecimal("40000.00"), policyholder));

        assertTrue(ex.getMessage().contains("future"));
        verify(claimRepository, never()).save(any(Claim.class));
    }

    @Test
    @DisplayName("Claims approved in an earlier policy year no longer use up this year's cover")
    void coverResetsEachPolicyYear() {
        policy.setStartDate(LocalDate.now().minusYears(1).minusMonths(2));   // now in its second year
        Claim lastYear = new Claim();
        lastYear.setAmountClaimed(new BigDecimal("1900000.00"));
        lastYear.setTreatmentDate(LocalDate.now().minusMonths(13));
        lastYear.setStatus(ClaimStatus.APPROVED);
        Claim thisYear = new Claim();
        thisYear.setAmountClaimed(new BigDecimal("150000.00"));
        thisYear.setTreatmentDate(LocalDate.now().minusMonths(1));
        thisYear.setStatus(ClaimStatus.APPROVED);
        when(claimRepository.findByPolicyAndStatus(policy, ClaimStatus.APPROVED))
                .thenReturn(List.of(lastYear, thisYear));

        // 2,000,000 limit - 150,000 approved this year; last year's 1,900,000 no longer counts.
        assertEquals(0, new BigDecimal("1850000.00").compareTo(claimService.remainingCoverage(policy)));
    }

    @Test
    @DisplayName("A claim that has already been decided cannot be decided again")
    void refusesSecondDecision() {
        testClaim.setStatus(ClaimStatus.APPROVED);
        when(claimRepository.findById(1L)).thenReturn(Optional.of(testClaim));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> claimService.decide(1L, ClaimStatus.REJECTED, "Changed my mind", claimsOfficer));

        assertTrue(ex.getMessage().contains("already been decided"));
        verify(claimRepository, never()).save(any(Claim.class));
    }

    @Test
    @DisplayName("An officer can only approve or reject a claim, not set any other status")
    void refusesOtherDecisionStatuses() {
        when(claimRepository.findById(1L)).thenReturn(Optional.of(testClaim));

        assertThrows(IllegalStateException.class,
                () -> claimService.decide(1L, ClaimStatus.WITHDRAWN, "Not a decision", claimsOfficer));

        verify(claimRepository, never()).save(any(Claim.class));
    }

    @Test
    @DisplayName("A claim for treatment before the policy started is refused")
    void refusesTreatmentBeforePolicyStart() {
        policy.setStartDate(LocalDate.now().minusMonths(2));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> claimService.submit(policy, policyholder, null, ClaimCategory.DENTAL, "City Dental",
                        LocalDate.now().minusMonths(3), "Filling", new BigDecimal("5000.00"), policyholder));

        assertTrue(ex.getMessage().contains("before this policy's cover started"));
        verify(claimRepository, never()).save(any(Claim.class));
    }
}
