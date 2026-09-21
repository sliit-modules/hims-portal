package com.medisure.hims.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** Sends premium-due reminders every morning, and once when the application starts. */
@Component
@Order(10)
public class PremiumReminderJob implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PremiumReminderJob.class);

    private final NotificationService notificationService;

    public PremiumReminderJob(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Scheduled(cron = "0 0 8 * * *")
    public void daily() {
        send();
    }

    @Override
    public void run(ApplicationArguments args) {
        send();
    }

    private void send() {
        int sent = notificationService.sendPremiumReminders(LocalDate.now());
        if (sent > 0) {
            log.info("Sent {} premium reminder(s)", sent);
        }
    }
}
