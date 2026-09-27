package com.medisure.hims.pattern.event;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Observer pattern: the subject. It keeps the list of observers and notifies each of them when an
 * event is published.
 *
 * <p>Spring hands the constructor every observer bean in the application, which is how observers
 * register themselves. Observers can also be attached and detached at runtime, which the unit
 * tests use.</p>
 */
@Component
public class DomainEvents {

    private final List<DomainEventObserver> observers = new ArrayList<>();

    public DomainEvents(List<DomainEventObserver> registered) {
        observers.addAll(registered);
    }

    public void register(DomainEventObserver observer) {
        if (!observers.contains(observer)) {
            observers.add(observer);
        }
    }

    public void unregister(DomainEventObserver observer) {
        observers.remove(observer);
    }

    public List<DomainEventObserver> observers() {
        return List.copyOf(observers);
    }

    /** Tells every observer about the event, in the order they registered. */
    public void publish(DomainEvent event) {
        for (DomainEventObserver observer : observers) {
            observer.onEvent(event);
        }
    }
}
