package com.medisure.hims.repository;

import com.medisure.hims.model.Claim;
import com.medisure.hims.model.ClaimDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClaimDocumentRepository extends JpaRepository<ClaimDocument, Long> {
    List<ClaimDocument> findByClaimOrderByUploadedAtAsc(Claim claim);

    /** One grouped query so a claims list can show attachment counts without an N+1. */
    @org.springframework.data.jpa.repository.Query(
            "select d.claim.id, count(d) from ClaimDocument d group by d.claim.id")
    List<Object[]> countGroupedByClaim();
}
