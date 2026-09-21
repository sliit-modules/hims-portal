package com.medisure.hims.repository;

import com.medisure.hims.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findAllByOrderByTimestampDesc();

    /** Changes only (pattern "VIEWED_%" excluded) or record views only (pattern included). */
    List<AuditLog> findByActionNotLikeOrderByTimestampDesc(String actionPattern);

    List<AuditLog> findByActionLikeOrderByTimestampDesc(String actionPattern);

    /** The latest reads of one person's records, for their "who has accessed my records" list. */
    List<AuditLog> findTop20ByEntityTypeAndEntityIdAndActionLikeOrderByTimestampDesc(
            String entityType, Long entityId, String actionPattern);
}
