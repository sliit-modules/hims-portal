package com.medisure.hims.service;

import com.medisure.hims.model.AuditLog;
import com.medisure.hims.model.User;
import com.medisure.hims.repository.AuditLogRepository;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void log(String entityType, Long entityId, String action, User performedBy, String notes) {
        auditLogRepository.save(new AuditLog(entityType, entityId, action, performedBy, notes));
    }
}
