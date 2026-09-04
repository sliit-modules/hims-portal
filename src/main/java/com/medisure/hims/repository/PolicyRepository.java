package com.medisure.hims.repository;

import com.medisure.hims.model.Policy;
import com.medisure.hims.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PolicyRepository extends JpaRepository<Policy, Long> {
    List<Policy> findByPolicyholder(User policyholder);
    Optional<Policy> findBySourceApplicationId(Long applicationId);
    Optional<Policy> findTopByPolicyCodeStartingWithOrderByPolicyCodeDesc(String prefix);
}
