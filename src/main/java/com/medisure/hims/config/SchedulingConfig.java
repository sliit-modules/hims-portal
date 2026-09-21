package com.medisure.hims.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on scheduled jobs such as the daily premium reminders. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
