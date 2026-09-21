package com.medisure.hims.service;

import com.medisure.hims.model.*;
import com.medisure.hims.repository.NotificationRepository;
import com.medisure.hims.repository.PolicyRepository;
import com.medisure.hims.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * In-app notifications (the bell in the top bar). Services call this when something happens that
 * a person needs to know about — the "Notify" steps in the activity diagrams.
 */
@Service
public class NotificationService {

    /** Premium reminders start this many days before the due date. */
    public static final int REMINDER_DAYS = 7;

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final PolicyRepository policyRepository;

    public NotificationService(NotificationRepository notificationRepository, UserRepository userRepository,
                               PolicyRepository policyRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.policyRepository = policyRepository;
    }

    public Notification notify(User recipient, String title, String message, String link) {
        return save(recipient, title, message, link, null);
    }

    /** Tells every enabled user with this role, e.g. all claims officers about a new claim. */
    public void notifyRole(Role role, String title, String message, String link) {
        userRepository.findByRole(role).stream()
                .filter(User::isEnabled)
                .forEach(user -> save(user, title, message, link, null));
    }

    public long unreadCount(User user) {
        return notificationRepository.countByRecipientAndReadAtIsNull(user);
    }

    public List<Notification> findFor(User user) {
        return notificationRepository.findTop50ByRecipientOrderByCreatedAtDesc(user);
    }

    /** Marks a notification read when its owner opens it. Nobody can open someone else's. */
    public Notification open(Long id, User user) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Notification not found"));
        if (!notification.getRecipient().getId().equals(user.getId())) {
            throw new AccessDeniedException("This notification belongs to someone else");
        }
        if (notification.getReadAt() == null) {
            notification.setReadAt(LocalDateTime.now());
            notificationRepository.save(notification);
        }
        return notification;
    }

    public int markAllRead(User user) {
        List<Notification> unread = notificationRepository.findByRecipientAndReadAtIsNull(user);
        LocalDateTime now = LocalDateTime.now();
        unread.forEach(n -> n.setReadAt(now));
        notificationRepository.saveAll(unread);
        return unread.size();
    }

    /**
     * Reminds each policyholder whose premium is due within {@value #REMINDER_DAYS} days or is
     * overdue. Each due date is reminded only once, however often this runs.
     *
     * @return how many reminders were sent
     */
    public int sendPremiumReminders(LocalDate today) {
        int sent = 0;
        for (Policy policy : policyRepository.findByStatusIn(List.of(PolicyStatus.ACTIVE, PolicyStatus.RENEWED))) {
            LocalDate due = policy.getNextDueDate();
            if (due == null || due.isAfter(today.plusDays(REMINDER_DAYS))) {
                continue;
            }
            String reference = "PREMIUM_DUE:" + policy.getPolicyCode() + ":" + due;
            if (notificationRepository.existsByRecipientAndReference(policy.getPolicyholder(), reference)) {
                continue;
            }
            boolean overdue = due.isBefore(today);
            save(policy.getPolicyholder(),
                    (overdue ? "Premium overdue: " : "Premium due: ") + policy.getPolicyCode(),
                    "LKR " + policy.getPremiumAmount() + (overdue ? " was due on " : " is due on ") + due,
                    "/payments/new?policyId=" + policy.getId(), reference);
            sent++;
        }
        return sent;
    }

    private Notification save(User recipient, String title, String message, String link, String reference) {
        if (recipient == null) {
            return null;
        }
        Notification notification = new Notification();
        notification.setRecipient(recipient);
        notification.setTitle(limit(title, 150));
        notification.setMessage(limit(message, 500));
        notification.setLink(link);
        notification.setReference(reference);
        notification.setCreatedAt(LocalDateTime.now());
        return notificationRepository.save(notification);
    }

    private static String limit(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
