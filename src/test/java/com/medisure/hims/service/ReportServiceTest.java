package com.medisure.hims.service;

import com.medisure.hims.model.*;
import com.medisure.hims.repository.ClaimRepository;
import com.medisure.hims.repository.InsurancePlanRepository;
import com.medisure.hims.repository.PaymentRepository;
import com.medisure.hims.repository.PolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
    private static final LocalDate TO = LocalDate.of(2026, 3, 31);

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private ClaimRepository claimRepository;

    @Mock
    private PolicyRepository policyRepository;

    @Mock
    private InsurancePlanRepository planRepository;

    @InjectMocks
    private ReportService reportService;

    private InsurancePlan family;
    private InsurancePlan solo;
    private Policy familyPolicy;
    private Policy soloPolicy;

    @BeforeEach
    void setUp() {
        family = plan(1L, "Family Shield");
        solo = plan(2L, "Solo Care");
        familyPolicy = policy(10L, family, PolicyStatus.ACTIVE);
        soloPolicy = policy(11L, solo, PolicyStatus.LAPSED);
    }

    private InsurancePlan plan(Long id, String name) {
        InsurancePlan plan = new InsurancePlan();
        plan.setId(id);
        plan.setPlanName(name);
        return plan;
    }

    private Policy policy(Long id, InsurancePlan plan, PolicyStatus status) {
        Policy policy = new Policy();
        policy.setId(id);
        policy.setPlan(plan);
        policy.setStatus(status);
        return policy;
    }

    private Payment payment(Policy policy, String amount, PaymentStatus status, LocalDateTime paidAt) {
        Payment payment = new Payment();
        payment.setPolicy(policy);
        payment.setAmount(new BigDecimal(amount));
        payment.setStatus(status);
        payment.setPaidAt(paidAt);
        return payment;
    }

    private Claim claim(Policy policy, String amount, ClaimStatus status, LocalDateTime submittedAt) {
        Claim claim = new Claim();
        claim.setPolicy(policy);
        claim.setAmountClaimed(new BigDecimal(amount));
        claim.setStatus(status);
        claim.setSubmittedAt(submittedAt);
        return claim;
    }

    private void givenData() {
        when(planRepository.findAll()).thenReturn(List.of(solo, family));
        when(policyRepository.findAll()).thenReturn(List.of(familyPolicy, soloPolicy));
        when(paymentRepository.findAll()).thenReturn(List.of(
                payment(familyPolicy, "10000.00", PaymentStatus.PAID, LocalDateTime.of(2026, 1, 15, 10, 0)),
                payment(familyPolicy, "10000.00", PaymentStatus.PAID, LocalDateTime.of(2026, 2, 15, 10, 0)),
                payment(familyPolicy, "10000.00", PaymentStatus.REFUNDED, LocalDateTime.of(2026, 2, 20, 10, 0)),
                payment(soloPolicy, "5000.00", PaymentStatus.CANCELLED, LocalDateTime.of(2026, 3, 1, 10, 0)),
                payment(familyPolicy, "10000.00", PaymentStatus.PAID, LocalDateTime.of(2025, 12, 31, 23, 0))));
        when(claimRepository.findAll()).thenReturn(List.of(
                claim(familyPolicy, "15000.00", ClaimStatus.APPROVED, LocalDateTime.of(2026, 2, 3, 9, 0)),
                claim(familyPolicy, "4000.00", ClaimStatus.REJECTED, LocalDateTime.of(2026, 2, 5, 9, 0)),
                claim(soloPolicy, "2500.00", ClaimStatus.SUBMITTED, LocalDateTime.of(2026, 3, 31, 18, 0)),
                claim(familyPolicy, "99000.00", ClaimStatus.APPROVED, LocalDateTime.of(2026, 4, 1, 9, 0))));
    }

    @Test
    @DisplayName("Premium income counts only PAID payments in the period")
    void incomeExcludesRefundsVoidsAndOtherPeriods() {
        givenData();

        ReportService.Report report = reportService.build(FROM, TO);

        assertEquals(0, new BigDecimal("20000.00").compareTo(report.total().premiumIncome()));
        assertEquals(2, report.total().payments());
    }

    @Test
    @DisplayName("Claims ratio is approved claims divided by premium income, per plan and overall")
    void claimsRatioPerPlanAndOverall() {
        givenData();

        ReportService.Report report = reportService.build(FROM, TO);

        assertEquals(new BigDecimal("75.0"), report.total().claimsRatio());   // 15,000 / 20,000
        assertEquals(3, report.total().claims());
        assertEquals(0, new BigDecimal("21500.00").compareTo(report.total().claimed()));

        ReportService.PlanRow familyRow = report.plans().get(0);     // sorted by name
        assertEquals("Family Shield", familyRow.planName());
        assertEquals(1, familyRow.policiesInForce());
        assertEquals(new BigDecimal("75.0"), familyRow.claimsRatio());

        ReportService.PlanRow soloRow = report.plans().get(1);
        assertEquals(0, soloRow.policiesInForce());                   // lapsed policy is not in force
        assertNull(soloRow.claimsRatio());                            // no income, so no ratio
    }

    @Test
    @DisplayName("The report breaks figures down by month and counts claims by status")
    void monthsAndStatusCounts() {
        givenData();

        ReportService.Report report = reportService.build(FROM, TO);

        assertEquals(3, report.months().size());
        assertEquals(0, new BigDecimal("10000.00").compareTo(report.months().get(1).premiumIncome()));
        assertEquals(0, new BigDecimal("15000.00").compareTo(report.months().get(1).approved()));
        assertEquals(1L, report.claimsByStatus().get(ClaimStatus.SUBMITTED));
        assertEquals(0L, report.claimsByStatus().get(ClaimStatus.VOIDED));
    }

    @Test
    @DisplayName("A period that ends before it starts is refused")
    void refusesBackwardsPeriod() {
        assertThrows(IllegalArgumentException.class, () -> reportService.build(TO, FROM));
    }

    @Test
    @DisplayName("CSV cells are quoted when needed and cannot be run as spreadsheet formulas")
    void csvCellsAreSafe() {
        assertEquals("Family Shield", ReportService.csvCell("Family Shield"));
        assertEquals("\"Shield, Plus\"", ReportService.csvCell("Shield, Plus"));
        assertEquals("\"The \"\"Gold\"\" plan\"", ReportService.csvCell("The \"Gold\" plan"));
        assertEquals("'=1+2", ReportService.csvCell("=1+2"));
        assertEquals("'@SUM(A1)", ReportService.csvCell("@SUM(A1)"));
        assertEquals("-120.50", ReportService.csvCell("-120.50"));
    }

    @Test
    @DisplayName("The CSV export has a row for each plan and the totals")
    void csvHasPlanRowsAndTotals() {
        givenData();

        String csv = reportService.toCsv(reportService.build(FROM, TO));

        assertTrue(csv.contains("Family Shield,1,2,20000.00,2,19000.00,1,15000.00,75.0\r\n"));
        assertTrue(csv.contains("All plans,1,2,20000.00,3,21500.00,1,15000.00,75.0\r\n"));
        assertTrue(csv.contains("2026-02,10000.00,15000.00\r\n"));
    }
}
