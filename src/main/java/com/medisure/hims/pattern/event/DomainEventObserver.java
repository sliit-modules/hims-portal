package com.medisure.hims.pattern.event;

/**
 * Observer pattern: the contract every observer implements so the subject can tell it that
 * something has happened. Adding a new reaction, such as sending an email, means writing one more
 * observer; no existing class changes.
 */
public interface DomainEventObserver {

    /** Called by the subject once for every published event. */
    void onEvent(DomainEvent event);
}
