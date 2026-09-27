package com.medisure.hims.pattern.event;

import com.medisure.hims.model.Claim;
import com.medisure.hims.model.User;

/** Concrete event: a claims officer has voided a claim as a duplicate of another one. */
public record ClaimVoidedEvent(Claim claim, Claim original, String reason, User actor) implements DomainEvent {

    @Override
    public String entityType() {
        return "Claim";
    }

    @Override
    public Long entityId() {
        return claim.getId();
    }

    @Override
    public String auditAction() {
        return "VOIDED";
    }

    @Override
    public String auditNotes() {
        return "Duplicate of " + original.getClaimCode() + ": " + reason;
    }

    @Override
    public User recipient() {
        return claim.getClaimant();
    }

    @Override
    public String notificationTitle() {
        return "Claim " + claim.getClaimCode() + " closed as a duplicate";
    }

    @Override
    public String notificationBody() {
        return "It repeats claim " + original.getClaimCode() + ", which is still being handled. " + reason;
    }

    @Override
    public String link() {
        return "/claims/" + claim.getId();
    }
}
