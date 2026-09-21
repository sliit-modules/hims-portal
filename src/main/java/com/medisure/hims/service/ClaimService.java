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
import java.time.Period;
import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class ClaimService {

    private final ClaimRepository claimRepository;
    private final CodeGenerator codeGenerator;
    private final AuditService auditService;
    private final NotificationService notificationService;

    public ClaimService(ClaimRepository claimRepository, CodeGenerator codeGenerator, AuditService auditService,
                        NotificationService notificationService) {
        this.claimRepository = claimRepository;
        this.codeGenerator = codeGenerator;
        this.auditService = auditService;
        this.notificationService = notificationService;
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

    /** Amount already approved against the coverage limit in the current policy year. */
    public BigDecimal approvedTotal(Policy policy) {
        return approvedTotal(policy, LocalDate.now());
    }

    /**
     * Amount approved in the policy year that contains the given date. The coverage limit is
     * annual, so claims treated in an earlier policy year no longer count against it.
     */
    public BigDecimal approvedTotal(Policy policy, LocalDate onDate) {
        LocalDate[] year = policyYear(policy, onDate);
        return claimRepository.findByPolicyAndStatus(policy, ClaimStatus.APPROVED).stream()
                .filter(c -> year == null || c.getTreatmentDate() == null
                        || (!c.getTreatmentDate().isBefore(year[0]) && c.getTreatmentDate().isBefore(year[1])))
                .map(Claim::getAmountClaimed)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Cover left in the current policy year. */
    public BigDecimal remainingCoverage(Policy policy) {
        return remainingCoverage(policy, LocalDate.now());
    }

    /** Cover left in the policy year that contains the given (treatment) date. */
    public BigDecimal remainingCoverage(Policy policy, LocalDate onDate) {
        return policy.getPlan().getCoverageLimit().subtract(approvedTotal(policy, onDate));
    }

    /**
     * The policy year containing the date, as [start, end): each year runs from the policy's start
     * date to its next anniversary. Null when the policy has no start date, in which case every
     * approved claim counts.
     */
    private LocalDate[] policyYear(Policy policy, LocalDate onDate) {
        LocalDate start = policy.getStartDate();
        if (start == null) {
            return null;
        }
        LocalDate date = onDate == null ? LocalDate.now() : onDate;
        int yearsIn = date.isBefore(start) ? 0 : Period.between(start, date).getYears();
        LocalDate yearStart = start.plusYears(yearsIn);
        return new LocalDate[]{yearStart, yearStart.plusYears(1)};
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

    /** A policy only accepts new claims while it is in force (active or renewed). */
    public boolean acceptsClaims(Policy policy) {
        return policy.getStatus() == PolicyStatus.ACTIVE || policy.getStatus() == PolicyStatus.RENEWED;
    }

    public Claim submit(Policy policy, User claimant, Dependent claimantDependent, ClaimCategory category,
                         String hospitalName, LocalDate treatmentDate, String diagnosisSummary,
                         BigDecimal amountClaimed, User actor) {
        if (!acceptsClaims(policy)) {
            throw new IllegalStateException("Claims can only be filed against an active policy; %s is %s"
                    .formatted(policy.getPolicyCode(), policy.getStatus()));
        }
        validateDetails(hospitalName, treatmentDate, diagnosisSummary, amountClaimed);
        if (policy.getStartDate() != null && treatmentDate.isBefore(policy.getStartDate())) {
            throw new IllegalStateException("The treatment date is before this policy's cover started on "
                    + policy.getStartDate());
        }
        BigDecimal remaining = remainingCoverage(policy, treatmentDate);
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
        notificationService.notifyRole(Role.CLAIMS_OFFICER, "New claim to review: " + saved.getClaimCode(),
                saved.getCategory() + " claim for LKR " + saved.getAmountClaimed() + " at " + saved.getHospitalName(),
                "/claims/" + saved.getId());
        return saved;
    }

    public Claim decide(Long id, ClaimStatus status, String notes, User actor) {
        if (actor.getRole() != Role.CLAIMS_OFFICER && actor.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Only a claims officer can decide a claim");
        }
        Claim claim = findById(id);
        if (claim.getStatus() != ClaimStatus.SUBMITTED) {
            throw new IllegalStateException("This claim has already been decided (" + claim.getStatus() + ")");
        }
        if (status != ClaimStatus.APPROVED && status != ClaimStatus.REJECTED) {
            throw new IllegalStateException("A claim can only be approved or rejected");
        }
        if (status == ClaimStatus.APPROVED) {
            BigDecimal remaining = remainingCoverage(claim.getPolicy(), claim.getTreatmentDate());
            if (claim.getAmountClaimed().compareTo(remaining) > 0) {
                throw new IllegalStateException(
                        "Cannot approve: claim exceeds the remaining coverage of %s on this policy".formatted(remaining));
            }
        }
        claim.setStatus(status);
        claim.setDecisionNotes(notes);
        Claim saved = claimRepository.save(claim);
        auditService.log("Claim", saved.getId(), "DECIDED:" + status, actor, notes);
        notificationService.notify(saved.getClaimant(),
                "Claim " + saved.getClaimCode() + (status == ClaimStatus.APPROVED ? " approved" : " rejected"),
                notes == null || notes.isBlank() ? "Open the claim for details." : notes,
                "/claims/" + saved.getId());
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

    /**
     * Mirrors the entity's constraints so a bad value comes back as a message on the form instead
     * of failing later, when the claim is saved, with a generic error page.
     */
    private void validateDetails(String hospitalName, LocalDate treatmentDate, String diagnosisSummary,
                                 BigDecimal amountClaimed) {
        if (hospitalName == null || hospitalName.isBlank()) {
            throw new IllegalStateException("Hospital name is required");
        }
        if (diagnosisSummary == null || diagnosisSummary.isBlank()) {
            throw new IllegalStateException("Diagnosis summary is required");
        }
        if (treatmentDate == null) {
            throw new IllegalStateException("Treatment date is required");
        }
        if (treatmentDate.isAfter(LocalDate.now())) {
            throw new IllegalStateException("Treatment date cannot be in the future");
        }
        if (amountClaimed == null || amountClaimed.signum() <= 0) {
            throw new IllegalStateException("Amount claimed must be positive");
        }
    }
}
