package com.medisure.hims.service;

import com.medisure.hims.model.*;
import com.medisure.hims.repository.UnderwritingApplicationRepository;
import com.medisure.hims.util.CodeGenerator;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class UnderwritingService {

    /** A suggested score of this or more is flagged as high risk. */
    public static final int HIGH_RISK_SCORE = 70;

    /** A chosen score further than this from the suggestion must come with a reason. */
    public static final int OVERRIDE_TOLERANCE = 10;

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

    /**
     * Suggests a risk score from fixed, published rules: age, declared pre-existing conditions,
     * dependents to be covered and BMI. It is only a suggestion — the underwriter decides.
     */
    public RiskAssessment assessRisk(UnderwritingApplication app) {
        List<RiskAssessment.Factor> factors = new ArrayList<>();
        factors.add(new RiskAssessment.Factor("Base score", 10));

        int age = app.getApplicantAge() == null ? 0 : app.getApplicantAge();
        if (age >= 60) {
            factors.add(new RiskAssessment.Factor("Age 60 or over", 40));
        } else if (age >= 45) {
            factors.add(new RiskAssessment.Factor("Age 45-59", 25));
        } else if (age >= 30) {
            factors.add(new RiskAssessment.Factor("Age 30-44", 10));
        } else {
            factors.add(new RiskAssessment.Factor("Age under 30", 0));
        }

        if (app.isHasPreExistingConditions()) {
            factors.add(new RiskAssessment.Factor("Declared pre-existing conditions", 25));
        }

        int dependents = app.getNumDependentsPlanned() == null ? 0 : app.getNumDependentsPlanned();
        if (dependents > 0) {
            factors.add(new RiskAssessment.Factor(dependents + " dependent(s) to be covered",
                    Math.min(15, dependents * 5)));
        }

        BigDecimal bmi = app.getApplicant() == null ? null : app.getApplicant().getBmi();
        if (bmi == null) {
            factors.add(new RiskAssessment.Factor("Height and weight not recorded", 0));
        } else if (bmi.compareTo(BigDecimal.valueOf(30)) >= 0) {
            factors.add(new RiskAssessment.Factor("BMI " + bmi + " (30 or over)", 10));
        } else if (bmi.compareTo(BigDecimal.valueOf(25)) >= 0) {
            factors.add(new RiskAssessment.Factor("BMI " + bmi + " (25-29.9)", 5));
        } else if (bmi.compareTo(new BigDecimal("18.5")) < 0) {
            factors.add(new RiskAssessment.Factor("BMI " + bmi + " (under 18.5)", 5));
        } else {
            factors.add(new RiskAssessment.Factor("BMI " + bmi + " (healthy range)", 0));
        }

        int score = Math.min(100, factors.stream().mapToInt(RiskAssessment.Factor::getPoints).sum());
        BigDecimal loading = score >= HIGH_RISK_SCORE ? BigDecimal.valueOf(50)
                : score >= 50 ? BigDecimal.valueOf(25)
                : score >= 30 ? BigDecimal.TEN
                : BigDecimal.ZERO;
        return new RiskAssessment(score, loading, score >= HIGH_RISK_SCORE, factors);
    }

    public UnderwritingApplication decide(Long id, ApplicationDecision decision, Integer riskScore,
                                           BigDecimal premiumLoadingPercent, String overrideReason, User actor) {
        if (actor.getRole() != Role.UNDERWRITER && actor.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Only underwriters can decide applications");
        }
        UnderwritingApplication app = findById(id);
        if (app.getDecision() != ApplicationDecision.PENDING) {
            throw new IllegalStateException("This application has already been decided (" + app.getDecision() + ")");
        }
        if (decision != ApplicationDecision.APPROVED && decision != ApplicationDecision.REJECTED) {
            throw new IllegalStateException("Choose whether to approve or reject the application");
        }
        if (riskScore == null || riskScore < 0 || riskScore > 100) {
            throw new IllegalStateException("Risk score must be a number from 0 to 100");
        }
        if (premiumLoadingPercent == null || premiumLoadingPercent.signum() < 0) {
            throw new IllegalStateException("Premium loading must be zero or more");
        }
        int suggested = assessRisk(app).getScore();
        String reason = overrideReason == null || overrideReason.isBlank() ? null : overrideReason.trim();
        if (Math.abs(riskScore - suggested) > OVERRIDE_TOLERANCE && reason == null) {
            throw new IllegalStateException("Your risk score differs from the suggested " + suggested
                    + " by more than " + OVERRIDE_TOLERANCE + " points, so please give a reason");
        }
        app.setDecision(decision);
        app.setRiskScore(riskScore);
        app.setSuggestedRiskScore(suggested);
        app.setRiskOverrideReason(reason);
        app.setPremiumLoadingPercent(premiumLoadingPercent);
        app.setDecidedAt(LocalDateTime.now());
        UnderwritingApplication saved = applicationRepository.save(app);
        auditService.log("UnderwritingApplication", saved.getId(), "DECIDED:" + decision, actor,
                "risk=" + riskScore + " (suggested " + suggested + ") loading=" + premiumLoadingPercent
                        + (reason != null ? "; reason: " + reason : ""));
        return saved;
    }

    /** The applicant takes back an application that has not been decided yet. */
    public UnderwritingApplication withdraw(Long id, User actor) {
        UnderwritingApplication app = findById(id);
        if (!app.getApplicant().getId().equals(actor.getId()) && actor.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Only the applicant can withdraw this application");
        }
        if (app.getDecision() != ApplicationDecision.PENDING) {
            throw new IllegalStateException("Only a pending application can be withdrawn");
        }
        app.setDecision(ApplicationDecision.WITHDRAWN);
        app.setDecidedAt(LocalDateTime.now());
        UnderwritingApplication saved = applicationRepository.save(app);
        auditService.log("UnderwritingApplication", saved.getId(), "WITHDRAWN", actor, saved.getApplicationCode());
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
