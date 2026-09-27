package com.medisure.hims.pattern.event;

import com.medisure.hims.service.NotificationService;
import org.springframework.stereotype.Component;

/**
 * Concrete observer: tells the person affected by the event. Events with no recipient, such as a
 * purely internal change, are ignored.
 */
@Component
public class NotificationObserver implements DomainEventObserver {

    private final NotificationService notificationService;

    public NotificationObserver(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public void onEvent(DomainEvent event) {
        if (event.recipient() == null) {
            return;
        }
        notificationService.notify(event.recipient(), event.notificationTitle(),
                event.notificationBody(), event.link());
    }
}
