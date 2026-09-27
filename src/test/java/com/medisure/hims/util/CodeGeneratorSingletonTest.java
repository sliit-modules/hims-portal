package com.medisure.hims.util;

import com.medisure.hims.repository.ClaimRepository;
import com.medisure.hims.repository.PolicyRepository;
import com.medisure.hims.repository.UnderwritingApplicationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

/** Singleton pattern: only one generator may exist, so two codes can never be issued for one number. */
@ExtendWith(MockitoExtension.class)
class CodeGeneratorSingletonTest {

    @Mock
    private UnderwritingApplicationRepository applicationRepository;

    @Mock
    private PolicyRepository policyRepository;

    @Mock
    private ClaimRepository claimRepository;

    private CodeGenerator found;

    @BeforeEach
    void takeTheInstanceAside() {
        found = CodeGenerator.peekInstance();          // whatever the application context left behind
        CodeGenerator.restoreInstance(null);
    }

    @AfterEach
    void putTheInstanceBack() {
        CodeGenerator.restoreInstance(found);
    }

    private CodeGenerator build() {
        return new CodeGenerator(applicationRepository, policyRepository, claimRepository);
    }

    @Test
    @DisplayName("The first generator becomes the one instance, reachable from the global access point")
    void firstInstanceBecomesTheSingleton() {
        CodeGenerator only = build();

        assertSame(only, CodeGenerator.getInstance());
    }

    @Test
    @DisplayName("A second generator is refused, because two would hand out the same number twice")
    void secondInstanceIsRefused() {
        build();

        IllegalStateException ex = assertThrows(IllegalStateException.class, this::build);

        assertTrue(ex.getMessage().contains("singleton"));
    }

    @Test
    @DisplayName("Asking for the instance before one exists fails clearly")
    void accessBeforeCreationIsRefused() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, CodeGenerator::getInstance);

        assertTrue(ex.getMessage().contains("not been created"));
    }
}
