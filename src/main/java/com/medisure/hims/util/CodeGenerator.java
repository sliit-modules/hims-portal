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
 *
 * <p>Singleton pattern: exactly one CodeGenerator may exist in the running application. Two
 * instances could read the same highest code at the same moment and issue the same number twice,
 * which is why every method that hands out a code is synchronized on that one instance. The
 * single instance is created and shared by Spring, whose beans are singleton scoped by default;
 * the guard in the constructor makes that guarantee explicit and fails fast if a second instance
 * is ever created. {@link #getInstance()} gives the classic global access point to it.</p>
 */
@Component
public class CodeGenerator {

    /** The one and only instance, kept for the global access point below. */
    private static volatile CodeGenerator instance;

    private final UnderwritingApplicationRepository applicationRepository;
    private final PolicyRepository policyRepository;
    private final ClaimRepository claimRepository;

    public CodeGenerator(UnderwritingApplicationRepository applicationRepository,
                          PolicyRepository policyRepository,
                          ClaimRepository claimRepository) {
        synchronized (CodeGenerator.class) {
            if (instance != null) {
                throw new IllegalStateException(
                        "CodeGenerator is a singleton: a second instance would issue duplicate codes");
            }
            this.applicationRepository = applicationRepository;
            this.policyRepository = policyRepository;
            this.claimRepository = claimRepository;
            instance = this;
        }
    }

    /**
     * The global access point of the Singleton pattern, for the few places that cannot receive the
     * generator by constructor injection.
     */
    public static CodeGenerator getInstance() {
        if (instance == null) {
            throw new IllegalStateException("CodeGenerator has not been created yet");
        }
        return instance;
    }

    /** The current instance, or null before one is created; for tests only. */
    static synchronized CodeGenerator peekInstance() {
        return instance;
    }

    /** Puts an instance back, so a test can prove the guard works and then restore what it found. */
    static synchronized void restoreInstance(CodeGenerator previous) {
        instance = previous;
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
