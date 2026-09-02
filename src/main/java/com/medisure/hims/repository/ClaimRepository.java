package com.medisure.hims.repository;

import com.medisure.hims.model.Claim;
import com.medisure.hims.model.ClaimStatus;
import com.medisure.hims.model.Policy;
import com.medisure.hims.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClaimRepository extends JpaRepository<Claim, Long> {
    List<Claim> findByPolicy(Policy policy);
    List<Claim> findByPolicyAndStatus(Policy policy, ClaimStatus status);
    List<Claim> findByClaimant(User claimant);
    java.util.Optional<Claim> findTopByClaimCodeStartingWithOrderByClaimCodeDesc(String prefix);
}
