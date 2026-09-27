package com.medisure.hims.pattern.billing;

import com.medisure.hims.model.PremiumFrequency;

import java.time.LocalDate;

/**
 * Strategy pattern: the family of algorithms for working out a premium billing cycle.
 *
 * <p>Every billing frequency answers the same two questions in its own way, so each one lives in
 * its own class instead of in a switch statement repeated across the services. New frequencies
 * (for example a half yearly plan) are added by writing one more strategy, with no change to the
 * services that use them.</p>
 */
public interface BillingCycleStrategy {

    /** The frequency this strategy is responsible for. */
    PremiumFrequency frequency();

    /** The end of the billing period that starts on the given date, which is also the next due date. */
    LocalDate periodEnd(LocalDate periodStart);

    /** How the cycle is described to the policyholder, for example on a receipt. */
    String describe();
}
