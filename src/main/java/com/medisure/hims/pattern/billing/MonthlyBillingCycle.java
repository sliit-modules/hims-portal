package com.medisure.hims.pattern.billing;

import com.medisure.hims.model.PremiumFrequency;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** Concrete strategy: a premium paid every month. */
@Component
public class MonthlyBillingCycle implements BillingCycleStrategy {

    @Override
    public PremiumFrequency frequency() {
        return PremiumFrequency.MONTHLY;
    }

    @Override
    public LocalDate periodEnd(LocalDate periodStart) {
        return periodStart.plusMonths(1);
    }

    @Override
    public String describe() {
        return "Paid monthly";
    }
}
