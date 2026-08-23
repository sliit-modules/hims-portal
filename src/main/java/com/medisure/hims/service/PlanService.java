package com.medisure.hims.service;

import com.medisure.hims.model.InsurancePlan;
import com.medisure.hims.model.PlanStatus;
import com.medisure.hims.model.User;
import com.medisure.hims.repository.InsurancePlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class PlanService {

    private final InsurancePlanRepository planRepository;
    private final AuditService auditService;

    public PlanService(InsurancePlanRepository planRepository, AuditService auditService) {
        this.planRepository = planRepository;
        this.auditService = auditService;
    }

    public List<InsurancePlan> findAll() {
        return planRepository.findAll();
    }

    public List<InsurancePlan> findActive() {
        return planRepository.findByStatus(PlanStatus.ACTIVE);
    }

    public InsurancePlan findById(Long id) {
        return planRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Plan not found"));
    }

    public InsurancePlan create(InsurancePlan plan, User actor) {
        plan.setStatus(PlanStatus.ACTIVE);
        InsurancePlan saved = planRepository.save(plan);
        auditService.log("InsurancePlan", saved.getId(), "CREATED", actor, saved.getPlanName());
        return saved;
    }

    public InsurancePlan update(Long id, InsurancePlan changes, User actor) {
        InsurancePlan plan = findById(id);
        plan.setPlanName(changes.getPlanName());
        plan.setDescription(changes.getDescription());
        plan.setPlanType(changes.getPlanType());
        plan.setPremiumRate(changes.getPremiumRate());
        plan.setCoverageLimit(changes.getCoverageLimit());
        InsurancePlan saved = planRepository.save(plan);
        auditService.log("InsurancePlan", saved.getId(), "UPDATED", actor, saved.getPlanName());
        return saved;
    }

    public void discontinue(Long id, User actor) {
        InsurancePlan plan = findById(id);
        plan.setStatus(PlanStatus.DISCONTINUED);
        planRepository.save(plan);
        auditService.log("InsurancePlan", plan.getId(), "DISCONTINUED", actor, plan.getPlanName());
    }
}
