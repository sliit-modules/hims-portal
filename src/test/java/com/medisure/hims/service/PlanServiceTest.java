package com.medisure.hims.service;

import com.medisure.hims.model.*;
import com.medisure.hims.repository.InsurancePlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlanServiceTest {

    @Mock
    private InsurancePlanRepository planRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private PlanService planService;

    private InsurancePlan samplePlan;
    private User testAdmin;

    @BeforeEach
    void setUp() {
        samplePlan = new InsurancePlan();
        samplePlan.setId(1L);
        samplePlan.setPlanName("Silver Care");
        samplePlan.setDescription("Essential individual cover");
        samplePlan.setPlanType(PlanType.INDIVIDUAL);
        samplePlan.setPremiumRate(new BigDecimal("5000.00"));
        samplePlan.setCoverageLimit(new BigDecimal("500000.00"));
        samplePlan.setStatus(PlanStatus.ACTIVE);

        testAdmin = new User();
        testAdmin.setId(10L);
        testAdmin.setNic("199001012345");
        testAdmin.setFullName("Karunarathna W.M.K.U.");
        testAdmin.setRole(Role.ADMIN);
    }

    @Test
    @DisplayName("A newly created plan is active and audited")
    void createsPlanAsActive() {
        when(planRepository.save(any(InsurancePlan.class))).thenReturn(samplePlan);

        InsurancePlan created = planService.create(samplePlan, testAdmin);

        assertNotNull(created);
        assertEquals(PlanStatus.ACTIVE, created.getStatus());
        verify(auditService).log(eq("InsurancePlan"), eq(1L), eq("CREATED"), eq(testAdmin), any());
    }

    @Test
    @DisplayName("Discontinuing a plan flips its status without deleting it")
    void discontinuesPlan() {
        when(planRepository.findById(1L)).thenReturn(Optional.of(samplePlan));

        planService.discontinue(1L, testAdmin);

        assertEquals(PlanStatus.DISCONTINUED, samplePlan.getStatus());
        verify(planRepository).save(samplePlan);
        verify(planRepository, never()).delete(any(InsurancePlan.class));
        verify(auditService).log(eq("InsurancePlan"), eq(1L), eq("DISCONTINUED"), eq(testAdmin), any());
    }

    @Test
    @DisplayName("Updating a plan changes its pricing and cover but keeps the same record")
    void updatesPlanPricing() {
        InsurancePlan changes = new InsurancePlan();
        changes.setPlanName("Silver Care Plus");
        changes.setDescription("Upgraded individual cover");
        changes.setPlanType(PlanType.INDIVIDUAL);
        changes.setPremiumRate(new BigDecimal("6200.00"));
        changes.setCoverageLimit(new BigDecimal("900000.00"));

        when(planRepository.findById(1L)).thenReturn(Optional.of(samplePlan));
        when(planRepository.save(any(InsurancePlan.class))).thenAnswer(inv -> inv.getArgument(0));

        InsurancePlan updated = planService.update(1L, changes, testAdmin);

        assertEquals("Silver Care Plus", updated.getPlanName());
        assertEquals(0, new BigDecimal("6200.00").compareTo(updated.getPremiumRate()));
        assertEquals(0, new BigDecimal("900000.00").compareTo(updated.getCoverageLimit()));
        assertEquals(1L, updated.getId(), "the existing plan record should be updated in place");
        verify(auditService).log(eq("InsurancePlan"), eq(1L), eq("UPDATED"), eq(testAdmin), any());
    }

    @Test
    @DisplayName("Only active plans are offered for sale")
    void listsOnlyActivePlans() {
        when(planRepository.findByStatus(PlanStatus.ACTIVE)).thenReturn(List.of(samplePlan));

        List<InsurancePlan> active = planService.findActive();

        assertEquals(1, active.size());
        assertEquals(PlanStatus.ACTIVE, active.get(0).getStatus());
    }
}
