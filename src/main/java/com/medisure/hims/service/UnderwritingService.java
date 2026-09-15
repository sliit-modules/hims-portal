package com.medisure.hims.service;

import com.medisure.hims.model.*;
import com.medisure.hims.repository.UnderwritingApplicationRepository;
import com.medisure.hims.util.CodeGenerator;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.Period;
import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class UnderwritingService {

    private final UnderwritingApplicationRepository applicationRepository;
    private final CodeGenerator codeGenerator;
    private final AuditService auditService;

    public UnderwritingService(UnderwritingApplicationRepository applicationRepository,
                                CodeGenerator codeGenerator, AuditService auditService) {
        this.applicationRepository = applicationRepository;
        this.codeGenerator = codeGenerator;
        this.auditService = auditService;
    }

    public List<UnderwritingApplication> findAllFor(User currentUser) {
        if (currentUser.getRole() == Role.UNDERWRITER || currentUser.getRole() == Role.ADMIN
                || currentUser.getRole() == Role.SALES_AGENT) {
            return applicationRepository.findAll();
        }
        return applicationRepository.findByApplicant(currentUser);
    }

    public UnderwritingApplication findById(Long id) {
        return applicationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Application not found"));
    }

    public UnderwritingApplication submit(User applicant, InsurancePlan plan, boolean hasPreExisting,
                                           String conditionsNotes, int numDependentsPlanned, User actor) {
        if (numDependentsPlanned < 0) {
            throw new IllegalStateException("Number of dependents cannot be negative");
        }
        UnderwritingApplication app = new UnderwritingApplication();
        app.setApplicationCode(codeGenerator.nextApplicationCode());
        app.setApplicant(applicant);
        app.setRequestedPlan(plan);
        app.setApplicantAge(Period.between(applicant.getDateOfBirth(), java.time.LocalDate.now()).getYears());
        app.setHasPreExistingConditions(hasPreExisting);
        app.setConditionsNotes(conditionsNotes);
        app.setNumDependentsPlanned(numDependentsPlanned);
        app.setDecision(ApplicationDecision.PENDING);
        UnderwritingApplication saved = applicationRepository.save(app);
        auditService.log("UnderwritingApplication", saved.getId(), "SUBMITTED", actor, saved.getApplicationCode());
        return saved;
    }

    public UnderwritingApplication decide(Long id, ApplicationDecision decision, Integer riskScore,
                                           java.math.BigDecimal premiumLoadingPercent, User actor) {
        if (actor.getRole() != Role.UNDERWRITER && actor.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Only underwriters can decide applications");
        }
        UnderwritingApplication app = findById(id);
        if (app.getDecision() != ApplicationDecision.PENDING) {
            throw new IllegalStateException("This application has already been decided");
        }
        if (decision == null || decision == ApplicationDecision.PENDING) {
            throw new IllegalStateException("Choose whether to approve or reject the application");
        }
        if (riskScore == null || riskScore < 0 || riskScore > 100) {
            throw new IllegalStateException("Risk score must be a number from 0 to 100");
        }
        if (premiumLoadingPercent == null || premiumLoadingPercent.signum() < 0) {
            throw new IllegalStateException("Premium loading must be zero or more");
        }
        app.setDecision(decision);
        app.setRiskScore(riskScore);
        app.setPremiumLoadingPercent(premiumLoadingPercent);
        app.setDecidedAt(LocalDateTime.now());
        UnderwritingApplication saved = applicationRepository.save(app);
        auditService.log("UnderwritingApplication", saved.getId(), "DECIDED:" + decision, actor,
                "risk=" + riskScore + " loading=" + premiumLoadingPercent);
        return saved;
    }

    public void assertVisible(UnderwritingApplication app, User currentUser) {
        boolean staff = currentUser.getRole() == Role.UNDERWRITER || currentUser.getRole() == Role.ADMIN
                || currentUser.getRole() == Role.SALES_AGENT;
        if (!staff && !app.getApplicant().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You may not view this application");
        }
    }
}
