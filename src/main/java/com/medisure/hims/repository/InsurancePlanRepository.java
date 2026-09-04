package com.medisure.hims.repository;

import com.medisure.hims.model.InsurancePlan;
import com.medisure.hims.model.PlanStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InsurancePlanRepository extends JpaRepository<InsurancePlan, Long> {
    List<InsurancePlan> findByStatus(PlanStatus status);
}
