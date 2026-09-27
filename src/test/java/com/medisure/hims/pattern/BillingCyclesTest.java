package com.medisure.hims.pattern;

import com.medisure.hims.model.PremiumFrequency;
import com.medisure.hims.pattern.billing.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Strategy pattern: each billing cycle works out its own period, and the context picks the right one. */
class BillingCyclesTest {

    private final BillingCycles cycles = new BillingCycles(List.of(
            new MonthlyBillingCycle(), new QuarterlyBillingCycle(), new AnnualBillingCycle()));

    private static final LocalDate START = LocalDate.of(2026, 9, 27);

    @Test
    @DisplayName("Each concrete strategy moves the period on by its own interval")
    void eachStrategyHasItsOwnInterval() {
        assertEquals(LocalDate.of(2026, 10, 27), new MonthlyBillingCycle().periodEnd(START));
        assertEquals(LocalDate.of(2026, 12, 27), new QuarterlyBillingCycle().periodEnd(START));
        assertEquals(LocalDate.of(2027, 9, 27), new AnnualBillingCycle().periodEnd(START));
    }

    @Test
    @DisplayName("The context delegates to the strategy that matches the policy's frequency")
    void contextDelegatesToTheMatchingStrategy() {
        assertEquals(LocalDate.of(2026, 10, 27), cycles.periodEnd(PremiumFrequency.MONTHLY, START));
        assertEquals(LocalDate.of(2026, 12, 27), cycles.periodEnd(PremiumFrequency.QUARTERLY, START));
        assertEquals(LocalDate.of(2027, 9, 27), cycles.periodEnd(PremiumFrequency.ANNUAL, START));
    }

    @Test
    @DisplayName("Every frequency the system offers has a strategy, and each one describes itself")
    void everyFrequencyIsCovered() {
        for (PremiumFrequency frequency : PremiumFrequency.values()) {
            BillingCycleStrategy strategy = cycles.forFrequency(frequency);
            assertEquals(frequency, strategy.frequency());
            assertFalse(cycles.describe(frequency).isBlank());
        }
    }

    @Test
    @DisplayName("A frequency with no strategy configured is refused instead of failing silently")
    void missingStrategyIsRefused() {
        BillingCycles onlyMonthly = new BillingCycles(List.of(new MonthlyBillingCycle()));
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> onlyMonthly.periodEnd(PremiumFrequency.ANNUAL, START));
        assertTrue(ex.getMessage().contains("ANNUAL"));
    }

    @Test
    @DisplayName("A new frequency is supported by adding one strategy, with no change to the context")
    void addingAStrategyNeedsNoChangeToTheContext() {
        BillingCycleStrategy halfYearly = new BillingCycleStrategy() {
            @Override
            public PremiumFrequency frequency() {
                return PremiumFrequency.ANNUAL;          // stands in for a new value on the enum
            }

            @Override
            public LocalDate periodEnd(LocalDate periodStart) {
                return periodStart.plusMonths(6);
            }

            @Override
            public String describe() {
                return "Paid twice a year";
            }
        };
        BillingCycles extended = new BillingCycles(List.of(new MonthlyBillingCycle(), halfYearly));
        assertEquals(LocalDate.of(2027, 3, 27), extended.periodEnd(PremiumFrequency.ANNUAL, START));
        assertEquals("Paid twice a year", extended.describe(PremiumFrequency.ANNUAL));
    }
}
