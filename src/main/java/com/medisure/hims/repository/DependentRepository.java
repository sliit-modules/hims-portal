package com.medisure.hims.repository;

import com.medisure.hims.model.Dependent;
import com.medisure.hims.model.Policy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DependentRepository extends JpaRepository<Dependent, Long> {
    List<Dependent> findByPolicy(Policy policy);
}
