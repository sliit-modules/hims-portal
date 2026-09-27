package com.medisure.hims.pattern.billing;

import com.medisure.hims.model.PremiumFrequency;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Context for the billing cycle strategies: it holds the strategies and delegates the work to the
 * one that matches the policy's frequency. The services ask this class rather than choosing a
 * strategy themselves, so there is no switch statement left in the business code.
 */
@Component
public class BillingCycles {

    private final Map<PremiumFrequency, BillingCycleStrategy> strategies = new EnumMap<>(PremiumFrequency.class);

    /** Spring injects every strategy bean, so a new frequency registers itself simply by existing. */
    public BillingCycles(List<BillingCycleStrategy> available) {
        available.forEach(strategy -> strategies.put(strategy.frequency(), strategy));
    }

    public BillingCycleStrategy forFrequency(PremiumFrequency frequency) {
        BillingCycleStrategy strategy = strategies.get(frequency);
        if (strategy == null) {
            throw new IllegalStateException("No billing cycle is configured for " + frequency);
        }
        return strategy;
    }

    /** Delegates to the matching strategy: the end of this billing period and the next due date. */
    public LocalDate periodEnd(PremiumFrequency frequency, LocalDate periodStart) {
        return forFrequency(frequency).periodEnd(periodStart);
    }

    public String describe(PremiumFrequency frequency) {
        return forFrequency(frequency).describe();
    }
}
