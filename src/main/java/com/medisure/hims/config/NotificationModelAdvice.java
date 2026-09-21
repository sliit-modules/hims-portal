package com.medisure.hims.config;

import com.medisure.hims.service.NotificationService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Gives every page the signed-in user's unread count, for the badge on the bell. */
@ControllerAdvice
public class NotificationModelAdvice {

    private final NotificationService notificationService;

    public NotificationModelAdvice(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @ModelAttribute("unreadNotifications")
    public long unreadNotifications() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return notificationService.unreadCount(principal.getUser());
        }
        return 0;
    }
}
