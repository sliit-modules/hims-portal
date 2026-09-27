package com.medisure.hims.pattern.event;

import com.medisure.hims.service.AuditService;
import org.springframework.stereotype.Component;

/**
 * Concrete observer: writes every published event to the audit log, so the trail required by the
 * proposal's non functional requirements is kept in one place rather than in each service.
 */
@Component
public class AuditObserver implements DomainEventObserver {

    private final AuditService auditService;

    public AuditObserver(AuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public void onEvent(DomainEvent event) {
        auditService.log(event.entityType(), event.entityId(), event.auditAction(),
                event.actor(), event.auditNotes());
    }
}
