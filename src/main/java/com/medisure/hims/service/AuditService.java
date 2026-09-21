package com.medisure.hims.service;

import com.medisure.hims.model.AuditLog;
import com.medisure.hims.model.User;
import com.medisure.hims.repository.AuditLogRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuditService {

    /** Record-access actions all start with this, so views can be kept apart from changes. */
    public static final String VIEW_PREFIX = "VIEWED_";
    public static final String VIEWED_PROFILE = "VIEWED_PROFILE";
    public static final String VIEWED_CLAIM = "VIEWED_CLAIM";
    public static final String VIEWED_DOCUMENT = "VIEWED_DOCUMENT";
    public static final String VIEWED_POLICY = "VIEWED_POLICY";
    public static final String VIEWED_APPLICATION = "VIEWED_APPLICATION";
    private static final String VIEW_PATTERN = VIEW_PREFIX + "%";

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void log(String entityType, Long entityId, String action, User performedBy, String notes) {
        auditLogRepository.save(new AuditLog(entityType, entityId, action, performedBy, notes));
    }

    /**
     * Records that someone opened another person's sensitive data. The entry is filed against the
     * person the data is about, so that person can later see who has looked at their records.
     * Reading your own data is not logged.
     */
    public void logAccess(User subject, User viewer, String action, String what) {
        if (subject == null || viewer == null || subject.getId() == null
                || subject.getId().equals(viewer.getId())) {
            return;
        }
        log("User", subject.getId(), action, viewer, what);
    }

    /** State-changing actions only, newest first. */
    public List<AuditLog> changes() {
        return auditLogRepository.findByActionNotLikeOrderByTimestampDesc(VIEW_PATTERN);
    }

    /** Record views only, newest first. */
    public List<AuditLog> recordAccess() {
        return auditLogRepository.findByActionLikeOrderByTimestampDesc(VIEW_PATTERN);
    }

    /** The latest 20 times someone else opened this person's records. */
    public List<AuditLog> accessHistoryFor(User subject) {
        return auditLogRepository.findTop20ByEntityTypeAndEntityIdAndActionLikeOrderByTimestampDesc(
                "User", subject.getId(), VIEW_PATTERN);
    }
}
