package com.medisure.hims.service;

import com.medisure.hims.model.InsurancePlan;
import com.medisure.hims.model.PlanStatus;
import com.medisure.hims.model.PlanType;
import com.medisure.hims.model.User;
import com.medisure.hims.repository.InsurancePlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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
        samplePlan.setPlanType(PlanType.INDIVIDUAL);
        samplePlan.setPremiumRate(new BigDecimal("5000.00"));
        samplePlan.setCoverageLimit(new BigDecimal("500000.00"));
        samplePlan.setStatus(PlanStatus.ACTIVE);

        testAdmin = new User();
        testAdmin.setId(10L);
        testAdmin.setUsername("admin");
    }

    @Test
    void testCreatePlan() {
        when(planRepository.save(any(InsurancePlan.class))).thenReturn(samplePlan);
        InsurancePlan created = planService.create(samplePlan, testAdmin);
        assertNotNull(created);
        assertEquals(PlanStatus.ACTIVE, created.getStatus());
        verify(auditService).log(eq("InsurancePlan"), eq(1L), eq("CREATED"), eq(testAdmin), any());
    }

    @Test
    void testDiscontinuePlan() {
        when(planRepository.findById(1L)).thenReturn(Optional.of(samplePlan));
        planService.discontinue(1L, testAdmin);
        assertEquals(PlanStatus.DISCONTINUED, samplePlan.getStatus());
        verify(planRepository).save(samplePlan);
    }
}
