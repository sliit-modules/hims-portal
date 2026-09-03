package com.medisure.hims.service;

import com.medisure.hims.model.*;
import com.medisure.hims.repository.ClaimRepository;
import com.medisure.hims.util.CodeGenerator;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class ClaimService {

    private final ClaimRepository claimRepository;
    private final CodeGenerator codeGenerator;
    private final AuditService auditService;

    public ClaimService(ClaimRepository claimRepository, CodeGenerator codeGenerator, AuditService auditService) {
        this.claimRepository = claimRepository;
        this.codeGenerator = codeGenerator;
        this.auditService = auditService;
    }

    public List<Claim> findAllFor(User currentUser) {
        if (currentUser.getRole() == Role.POLICYHOLDER) {
            return claimRepository.findByClaimant(currentUser);
        }
        return claimRepository.findAll();
    }

    public Claim findById(Long id) {
        return claimRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Claim not found"));
    }

    public void assertVisible(Claim claim, User currentUser) {
        if (currentUser.getRole() == Role.POLICYHOLDER
                && !claim.getClaimant().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You may not view this claim");
        }
    }

    /** Amount already approved against this policy's annual coverage limit. */
    public BigDecimal approvedTotal(Policy policy) {
        return claimRepository.findByPolicyAndStatus(policy, ClaimStatus.APPROVED).stream()
                .map(Claim::getAmountClaimed)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal remainingCoverage(Policy policy) {
        return policy.getPlan().getCoverageLimit().subtract(approvedTotal(policy));
    }

    /** Percentage (0-100) of a policy's annual coverage already used by approved claims. */
    public double usedPercent(Policy policy) {
        BigDecimal limit = policy.getPlan().getCoverageLimit();
        if (limit.signum() == 0) {
            return 0.0;
        }
        return approvedTotal(policy).divide(limit, 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).doubleValue();
    }

    public Claim submit(Policy policy, User claimant, Dependent claimantDependent, ClaimCategory category,
                         String hospitalName, LocalDate treatmentDate, String diagnosisSummary,
                         BigDecimal amountClaimed, User actor) {
        BigDecimal remaining = remainingCoverage(policy);
        if (amountClaimed.compareTo(remaining) > 0) {
            throw new IllegalStateException(
                    "Claim of %s exceeds the remaining coverage of %s on this policy".formatted(amountClaimed, remaining));
        }
        Claim claim = new Claim();
        claim.setClaimCode(codeGenerator.nextClaimCode());
        claim.setPolicy(policy);
        claim.setClaimant(claimant);
        claim.setClaimantDependent(claimantDependent);
        claim.setCategory(category);
        claim.setHospitalName(hospitalName);
        claim.setTreatmentDate(treatmentDate);
        claim.setDiagnosisSummary(diagnosisSummary);
        claim.setAmountClaimed(amountClaimed);
        claim.setStatus(ClaimStatus.SUBMITTED);
        claim.setSubmittedAt(LocalDateTime.now());
        Claim saved = claimRepository.save(claim);
        auditService.log("Claim", saved.getId(), "SUBMITTED", actor, saved.getClaimCode());
        return saved;
    }

    public Claim decide(Long id, ClaimStatus status, String notes, User actor) {
        if (actor.getRole() != Role.CLAIMS_OFFICER && actor.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Only a claims officer can decide a claim");
        }
        Claim claim = findById(id);
        if (status == ClaimStatus.APPROVED) {
            BigDecimal remaining = remainingCoverage(claim.getPolicy());
            if (claim.getAmountClaimed().compareTo(remaining) > 0) {
                throw new IllegalStateException(
                        "Cannot approve: claim exceeds the remaining coverage of %s on this policy".formatted(remaining));
            }
        }
        claim.setStatus(status);
        claim.setDecisionNotes(notes);
        Claim saved = claimRepository.save(claim);
        auditService.log("Claim", saved.getId(), "DECIDED:" + status, actor, notes);
        return saved;
    }

    public void withdraw(Long id, User actor) {
        Claim claim = findById(id);
        if (!claim.getClaimant().getId().equals(actor.getId()) && actor.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("You may only withdraw your own claim");
        }
        if (claim.getStatus() != ClaimStatus.SUBMITTED) {
            throw new IllegalStateException("Only a pending claim can be withdrawn");
        }
        claim.setStatus(ClaimStatus.WITHDRAWN);
        claimRepository.save(claim);
        auditService.log("Claim", claim.getId(), "WITHDRAWN", actor, claim.getClaimCode());
    }
}
