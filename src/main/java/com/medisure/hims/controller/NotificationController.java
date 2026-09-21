package com.medisure.hims.controller;

import com.medisure.hims.config.UserPrincipal;
import com.medisure.hims.model.Notification;
import com.medisure.hims.service.NotificationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public String list(@AuthenticationPrincipal UserPrincipal principal, Model model) {
        model.addAttribute("notifications", notificationService.findFor(principal.getUser()));
        return "notifications/list";
    }

    /** Marks the notification read and opens the page it is about. */
    @GetMapping("/{id}/open")
    public String open(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        Notification notification = notificationService.open(id, principal.getUser());
        String link = notification.getLink();
        // Only follow links inside the app, never to another site.
        boolean internal = link != null && link.startsWith("/") && !link.startsWith("//");
        return "redirect:" + (internal ? link : "/notifications");
    }

    @PostMapping("/read-all")
    public String markAllRead(@AuthenticationPrincipal UserPrincipal principal) {
        notificationService.markAllRead(principal.getUser());
        return "redirect:/notifications";
    }
}
