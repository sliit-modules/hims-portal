package com.medisure.hims.pattern.billing;

import com.medisure.hims.model.PremiumFrequency;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** Concrete strategy: a premium paid once a year. */
@Component
public class AnnualBillingCycle implements BillingCycleStrategy {

    @Override
    public PremiumFrequency frequency() {
        return PremiumFrequency.ANNUAL;
    }

    @Override
    public LocalDate periodEnd(LocalDate periodStart) {
        return periodStart.plusYears(1);
    }

    @Override
    public String describe() {
        return "Paid once a year";
    }
}
