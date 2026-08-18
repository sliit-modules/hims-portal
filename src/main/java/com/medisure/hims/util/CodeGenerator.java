package com.medisure.hims.util;

import com.medisure.hims.model.Claim;
import com.medisure.hims.model.Policy;
import com.medisure.hims.model.UnderwritingApplication;
import com.medisure.hims.repository.ClaimRepository;
import com.medisure.hims.repository.PolicyRepository;
import com.medisure.hims.repository.UnderwritingApplicationRepository;
import org.springframework.stereotype.Component;

import java.time.Year;

/**
 * Produces human-readable document numbers (APP-2026-000042, POL-2026-000042, CLM-2026-000042).
 *
 * The next number is derived from the highest code already issued for that prefix rather than
 * from a row count: counting breaks as soon as the sequence has gaps (deleted rows, or codes
 * seeded out of order), which would hand out a number that is already taken.
 */
@Component
public class CodeGenerator {

    private final UnderwritingApplicationRepository applicationRepository;
    private final PolicyRepository policyRepository;
    private final ClaimRepository claimRepository;

    public CodeGenerator(UnderwritingApplicationRepository applicationRepository,
                          PolicyRepository policyRepository,
                          ClaimRepository claimRepository) {
        this.applicationRepository = applicationRepository;
        this.policyRepository = policyRepository;
        this.claimRepository = claimRepository;
    }

    public synchronized String nextApplicationCode() {
        String prefix = "APP-" + Year.now().getValue() + "-";
        String highest = applicationRepository.findTopByApplicationCodeStartingWithOrderByApplicationCodeDesc(prefix)
                .map(UnderwritingApplication::getApplicationCode).orElse(null);
        return prefix + pad(nextSequence(highest, prefix));
    }

    public synchronized String nextPolicyCode() {
        String prefix = "POL-" + Year.now().getValue() + "-";
        String highest = policyRepository.findTopByPolicyCodeStartingWithOrderByPolicyCodeDesc(prefix)
                .map(Policy::getPolicyCode).orElse(null);
        return prefix + pad(nextSequence(highest, prefix));
    }

    public synchronized String nextClaimCode() {
        String prefix = "CLM-" + Year.now().getValue() + "-";
        String highest = claimRepository.findTopByClaimCodeStartingWithOrderByClaimCodeDesc(prefix)
                .map(Claim::getClaimCode).orElse(null);
        return prefix + pad(nextSequence(highest, prefix));
    }

    private long nextSequence(String highestCode, String prefix) {
        if (highestCode == null) {
            return 1;
        }
        try {
            return Long.parseLong(highestCode.substring(prefix.length())) + 1;
        } catch (NumberFormatException ex) {
            return 1;
        }
    }

    private String pad(long n) {
        return String.format("%06d", n);
    }
}
