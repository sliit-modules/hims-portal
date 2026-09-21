package com.medisure.hims.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** An in-app message shown under the bell in the top bar, e.g. "Claim CLM-2026-000042 approved". */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(length = 500)
    private String message;

    /** The page to open when the notification is clicked, always a path inside the app. */
    @Column(length = 255)
    private String link;

    /** Identifies a one-off reminder (such as one premium due date) so it is only ever sent once. */
    @Column(length = 120)
    private String reference;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime readAt;

    public boolean isUnread() {
        return readAt == null;
    }
}
