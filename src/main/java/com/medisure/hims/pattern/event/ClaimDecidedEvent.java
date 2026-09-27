package com.medisure.hims.pattern.event;

import com.medisure.hims.model.Claim;
import com.medisure.hims.model.ClaimStatus;
import com.medisure.hims.model.User;

/** Concrete event: a claims officer has approved or rejected a claim. */
public record ClaimDecidedEvent(Claim claim, ClaimStatus status, String notes, User actor) implements DomainEvent {

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
        return "DECIDED:" + status;
    }

    @Override
    public String auditNotes() {
        return notes;
    }

    @Override
    public User recipient() {
        return claim.getClaimant();
    }

    @Override
    public String notificationTitle() {
        return "Claim " + claim.getClaimCode() + (status == ClaimStatus.APPROVED ? " approved" : " rejected");
    }

    @Override
    public String notificationBody() {
        return notes == null || notes.isBlank() ? "Open the claim for details." : notes;
    }

    @Override
    public String link() {
        return "/claims/" + claim.getId();
    }
}
