package com.medisure.hims.repository;

import com.medisure.hims.model.Payment;
import com.medisure.hims.model.Policy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByPolicy(Policy policy);
    List<Payment> findByPolicyPolicyholder(com.medisure.hims.model.User policyholder);
}
