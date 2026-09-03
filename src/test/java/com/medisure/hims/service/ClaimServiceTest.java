package com.medisure.hims.service;

import com.medisure.hims.model.*;
import com.medisure.hims.repository.ClaimRepository;
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
class ClaimServiceTest {

    @Mock
    private ClaimRepository claimRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private ClaimService claimService;

    private Claim testClaim;
    private User testOfficer;

    @BeforeEach
    void setUp() {
        testClaim = new Claim();
        testClaim.setId(1L);
        testClaim.setClaimNumber("CLM-2026-000001");
        testClaim.setClaimAmount(new BigDecimal("75000.00"));
        testClaim.setStatus(ClaimStatus.PENDING);

        testOfficer = new User();
        testOfficer.setId(50L);
        testOfficer.setUsername("claims_officer");
    }

    @Test
    void testAdjudicateApproved() {
        when(claimRepository.findById(1L)).thenReturn(Optional.of(testClaim));
        when(claimRepository.save(any(Claim.class))).thenReturn(testClaim);

        claimService.adjudicate(1L, ClaimStatus.APPROVED, new BigDecimal("70000.00"), "Covered under surgical benefit", testOfficer);
        assertEquals(ClaimStatus.APPROVED, testClaim.getStatus());
        assertEquals(new BigDecimal("70000.00"), testClaim.getApprovedAmount());
        verify(auditService).log(eq("Claim"), eq(1L), eq("ADJUDICATED"), eq(testOfficer), any());
    }

    @Test
    void testAdjudicateRejected() {
        when(claimRepository.findById(1L)).thenReturn(Optional.of(testClaim));
        when(claimRepository.save(any(Claim.class))).thenReturn(testClaim);

        claimService.adjudicate(1L, ClaimStatus.REJECTED, BigDecimal.ZERO, "Exceeds annual outpatient limit", testOfficer);
        assertEquals(ClaimStatus.REJECTED, testClaim.getStatus());
    }
}
