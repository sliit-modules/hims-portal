package com.medisure.hims.repository;

import com.medisure.hims.model.UnderwritingApplication;
import com.medisure.hims.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UnderwritingApplicationRepository extends JpaRepository<UnderwritingApplication, Long> {
    List<UnderwritingApplication> findByApplicant(User applicant);
    java.util.Optional<UnderwritingApplication> findTopByApplicationCodeStartingWithOrderByApplicationCodeDesc(String prefix);
}
