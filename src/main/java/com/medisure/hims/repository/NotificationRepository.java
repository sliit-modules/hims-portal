package com.medisure.hims.repository;

import com.medisure.hims.model.Notification;
import com.medisure.hims.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findTop50ByRecipientOrderByCreatedAtDesc(User recipient);

    List<Notification> findByRecipientAndReadAtIsNull(User recipient);

    long countByRecipientAndReadAtIsNull(User recipient);

    boolean existsByRecipientAndReference(User recipient, String reference);
}
