package com.medisure.hims.pattern.billing;

import com.medisure.hims.model.PremiumFrequency;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** Concrete strategy: a premium paid every three months. */
@Component
public class QuarterlyBillingCycle implements BillingCycleStrategy {

    @Override
    public PremiumFrequency frequency() {
        return PremiumFrequency.QUARTERLY;
    }

    @Override
    public LocalDate periodEnd(LocalDate periodStart) {
        return periodStart.plusMonths(3);
    }

    @Override
    public String describe() {
        return "Paid every three months";
    }
}
