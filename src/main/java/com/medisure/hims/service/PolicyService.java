package com.medisure.hims.service;

import com.medisure.hims.model.*;
import com.medisure.hims.repository.DependentRepository;
import com.medisure.hims.repository.PolicyRepository;
import com.medisure.hims.util.CodeGenerator;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class PolicyService {

    private final PolicyRepository policyRepository;
    private final DependentRepository dependentRepository;
    private final CodeGenerator codeGenerator;
    private final AuditService auditService;

    public PolicyService(PolicyRepository policyRepository, DependentRepository dependentRepository,
                          CodeGenerator codeGenerator, AuditService auditService) {
        this.policyRepository = policyRepository;
        this.dependentRepository = dependentRepository;
        this.codeGenerator = codeGenerator;
        this.auditService = auditService;
    }

    public List<Policy> findAllFor(User currentUser) {
        if (currentUser.getRole() == Role.POLICYHOLDER) {
            return policyRepository.findByPolicyholder(currentUser);
        }
        return policyRepository.findAll();
    }

    public Policy findById(Long id) {
        return policyRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Policy not found"));
    }

    public boolean existsForApplication(Long applicationId) {
        return policyRepository.findBySourceApplicationId(applicationId).isPresent();
    }

    public void assertVisible(Policy policy, User currentUser) {
        if (currentUser.getRole() == Role.POLICYHOLDER
                && !policy.getPolicyholder().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You may not view this policy");
        }
    }

    public Policy issueFromApplication(UnderwritingApplication application, PremiumFrequency frequency, User actor) {
        if (application.getDecision() != ApplicationDecision.APPROVED) {
            throw new IllegalStateException("Only an approved application can be issued as a policy");
        }
        if (policyRepository.findBySourceApplicationId(application.getId()).isPresent()) {
            throw new IllegalStateException("A policy has already been issued for this application");
        }

        BigDecimal loadingFactor = BigDecimal.ONE.add(
                application.getPremiumLoadingPercent().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
        BigDecimal premium = application.getRequestedPlan().getPremiumRate()
                .multiply(loadingFactor).setScale(2, RoundingMode.HALF_UP);

        Policy policy = new Policy();
        policy.setPolicyCode(codeGenerator.nextPolicyCode());
        policy.setPolicyholder(application.getApplicant());
        policy.setPlan(application.getRequestedPlan());
        policy.setSourceApplication(application);
        policy.setPremiumAmount(premium);
        policy.setPremiumFrequency(frequency);
        policy.setStartDate(LocalDate.now());
        policy.setEndDate(LocalDate.now().plusYears(1));
        policy.setNextDueDate(nextDueDate(LocalDate.now(), frequency));
        policy.setStatus(PolicyStatus.ACTIVE);

        Policy saved = policyRepository.save(policy);
        auditService.log("Policy", saved.getId(), "ISSUED", actor, saved.getPolicyCode());
        return saved;
    }

    public Policy renewOrModify(Long id, LocalDate newEndDate, PremiumFrequency frequency, User actor) {
        Policy policy = findById(id);
        policy.setEndDate(newEndDate);
        policy.setPremiumFrequency(frequency);
        policy.setStatus(PolicyStatus.RENEWED);
        Policy saved = policyRepository.save(policy);
        auditService.log("Policy", saved.getId(), "RENEWED", actor, "new end date " + newEndDate);
        return saved;
    }

    public void cancel(Long id, User actor) {
        Policy policy = findById(id);
        policy.setStatus(PolicyStatus.CANCELLED);
        policyRepository.save(policy);
        auditService.log("Policy", policy.getId(), "CANCELLED", actor, policy.getPolicyCode());
    }

    public Dependent addDependent(Long policyId, Dependent dependent, User actor) {
        Policy policy = findById(policyId);
        if (policy.getPlan().getPlanType() != PlanType.FAMILY) {
            throw new IllegalStateException("Dependents can only be added to a FAMILY plan policy");
        }
        if (dependent.isNicRequired() && (dependent.getNic() == null || dependent.getNic().isBlank())) {
            throw new IllegalStateException(
                    "%s is %d years old — a NIC is required for covered members aged 18 and over"
                            .formatted(dependent.getFullName(), dependent.getAge()));
        }
        // An adult's details are theirs to share: they must agree themselves. For a minor, the
        // policyholder's own consent covers them as parent or guardian.
        if (dependent.isNicRequired()) {
            if (!dependent.isConsentConfirmed()) {
                throw new IllegalStateException(
                        "%s is %d, so please confirm they have consented to their personal and medical details being recorded"
                                .formatted(dependent.getFullName(), dependent.getAge()));
            }
            dependent.setConsentConfirmedAt(LocalDateTime.now());
        }
        dependent.setPolicy(policy);
        Dependent saved = dependentRepository.save(dependent);
        auditService.log("Dependent", saved.getId(), "ADDED", actor, "policy " + policy.getPolicyCode());
        return saved;
    }

    public void removeDependent(Long dependentId, User actor) {
        Dependent dependent = dependentRepository.findById(dependentId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Dependent not found"));
        dependentRepository.delete(dependent);
        auditService.log("Dependent", dependentId, "REMOVED", actor, dependent.getFullName());
    }

    private LocalDate nextDueDate(LocalDate from, PremiumFrequency frequency) {
        return switch (frequency) {
            case MONTHLY -> from.plusMonths(1);
            case QUARTERLY -> from.plusMonths(3);
            case ANNUAL -> from.plusYears(1);
        };
    }
}
