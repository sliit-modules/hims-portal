package com.medisure.hims.pattern.event;

import com.medisure.hims.model.User;

/**
 * Observer pattern: something that has happened in the business, which interested observers may
 * react to.
 *
 * <p>An event carries everything an observer needs: what changed, who changed it, the entry for
 * the audit log and the message for the person affected. The service that caused the change only
 * publishes the event; it does not know, and does not need to know, who is listening.</p>
 */
public interface DomainEvent {

    /** The kind of record the event is about, for example "Claim". */
    String entityType();

    /** The id of that record. */
    Long entityId();

    /** The member of staff or member who caused the change. */
    User actor();

    /** The action to write to the audit log, for example "DECIDED:APPROVED". */
    String auditAction();

    /** The note to write with the audit entry. */
    String auditNotes();

    /** The person to tell, or null when the event needs no notification. */
    User recipient();

    /** The title of that notification. */
    String notificationTitle();

    /** The body of that notification. */
    String notificationBody();

    /** The page the notification links to. */
    String link();
}
