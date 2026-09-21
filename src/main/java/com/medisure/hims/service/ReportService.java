package com.medisure.hims.service;

import com.medisure.hims.model.*;
import com.medisure.hims.repository.ClaimRepository;
import com.medisure.hims.repository.InsurancePlanRepository;
import com.medisure.hims.repository.PaymentRepository;
import com.medisure.hims.repository.PolicyRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.function.Predicate;

/**
 * Management figures for a period: premium income, claims and the claims ratio, per plan and per
 * month. Premium income counts only payments that are still PAID, so refunded and voided payments
 * are left out. Claims are counted in the period they were submitted.
 */
@Service
public class ReportService {

    private final PaymentRepository paymentRepository;
    private final ClaimRepository claimRepository;
    private final PolicyRepository policyRepository;
    private final InsurancePlanRepository planRepository;

    public ReportService(PaymentRepository paymentRepository, ClaimRepository claimRepository,
                         PolicyRepository policyRepository, InsurancePlanRepository planRepository) {
        this.paymentRepository = paymentRepository;
        this.claimRepository = claimRepository;
        this.policyRepository = policyRepository;
        this.planRepository = planRepository;
    }

    /** One plan's figures (or, with planName "All plans", the totals). */
    public record PlanRow(String planName, long policiesInForce, long payments, BigDecimal premiumIncome,
                          long claims, BigDecimal claimed, long approvedClaims, BigDecimal approved,
                          BigDecimal claimsRatio) {
    }

    /** One month's premium income and approved claims. */
    public record MonthRow(YearMonth month, BigDecimal premiumIncome, BigDecimal approved) {
    }

    public record Report(LocalDate from, LocalDate to, List<PlanRow> plans, PlanRow total,
                         List<MonthRow> months, Map<ClaimStatus, Long> claimsByStatus,
                         LocalDateTime generatedAt) {
    }

    public Report build(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Choose both a start and an end date");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("The start date must be on or before the end date");
        }
        Predicate<LocalDateTime> inPeriod = at -> at != null
                && !at.toLocalDate().isBefore(from) && !at.toLocalDate().isAfter(to);

        List<Payment> income = paymentRepository.findAll().stream()
                .filter(p -> p.getStatus() == PaymentStatus.PAID && inPeriod.test(p.getPaidAt()))
                .toList();
        List<Claim> claims = claimRepository.findAll().stream()
                .filter(c -> inPeriod.test(c.getSubmittedAt()))
                .toList();
        List<Policy> inForce = policyRepository.findAll().stream()
                .filter(p -> p.getStatus() == PolicyStatus.ACTIVE || p.getStatus() == PolicyStatus.RENEWED)
                .toList();

        List<PlanRow> plans = planRepository.findAll().stream()
                .sorted(Comparator.comparing(InsurancePlan::getPlanName))
                .map(plan -> row(plan.getPlanName(),
                        inForce.stream().filter(p -> p.getPlan().getId().equals(plan.getId())).count(),
                        income.stream().filter(p -> p.getPolicy().getPlan().getId().equals(plan.getId())).toList(),
                        claims.stream().filter(c -> c.getPolicy().getPlan().getId().equals(plan.getId())).toList()))
                .toList();
        PlanRow total = row("All plans", inForce.size(), income, claims);

        Map<ClaimStatus, Long> byStatus = new EnumMap<>(ClaimStatus.class);
        for (ClaimStatus status : ClaimStatus.values()) {
            byStatus.put(status, claims.stream().filter(c -> c.getStatus() == status).count());
        }

        List<MonthRow> months = new ArrayList<>();
        for (YearMonth m = YearMonth.from(from); !m.isAfter(YearMonth.from(to)); m = m.plusMonths(1)) {
            YearMonth month = m;
            months.add(new MonthRow(month,
                    sum(income.stream().filter(p -> YearMonth.from(p.getPaidAt()).equals(month))
                            .map(Payment::getAmount)),
                    sum(claims.stream().filter(c -> c.getStatus() == ClaimStatus.APPROVED
                                    && YearMonth.from(c.getSubmittedAt()).equals(month))
                            .map(Claim::getAmountClaimed))));
        }
        return new Report(from, to, plans, total, months, byStatus, LocalDateTime.now());
    }

    private PlanRow row(String name, long policiesInForce, List<Payment> payments, List<Claim> claims) {
        BigDecimal premiumIncome = sum(payments.stream().map(Payment::getAmount));
        List<Claim> approvedClaims = claims.stream().filter(c -> c.getStatus() == ClaimStatus.APPROVED).toList();
        BigDecimal approved = sum(approvedClaims.stream().map(Claim::getAmountClaimed));
        return new PlanRow(name, policiesInForce, payments.size(), premiumIncome,
                claims.size(), sum(claims.stream().map(Claim::getAmountClaimed)),
                approvedClaims.size(), approved, claimsRatio(approved, premiumIncome));
    }

    /**
     * Approved claims as a percentage of premium income, to one decimal place. Null when there was
     * no income in the period, because the ratio is then meaningless.
     */
    public static BigDecimal claimsRatio(BigDecimal approved, BigDecimal premiumIncome) {
        if (premiumIncome == null || premiumIncome.signum() == 0) {
            return null;
        }
        return approved.multiply(BigDecimal.valueOf(100)).divide(premiumIncome, 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal sum(java.util.stream.Stream<BigDecimal> amounts) {
        return amounts.filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ------------------------------------------------------------------ CSV export

    /** The report as CSV, which opens directly in Excel. */
    public String toCsv(Report report) {
        StringBuilder csv = new StringBuilder();
        line(csv, "MediSure Lanka - Management Report");
        line(csv, "Period", report.from().toString(), report.to().toString());
        line(csv, "Generated", report.generatedAt().withNano(0).toString());
        line(csv);
        line(csv, "Plan", "Policies in force", "Payments", "Premium income (LKR)", "Claims",
                "Amount claimed (LKR)", "Approved claims", "Approved amount (LKR)", "Claims ratio (%)");
        for (PlanRow row : report.plans()) {
            planLine(csv, row);
        }
        planLine(csv, report.total());
        line(csv);
        line(csv, "Month", "Premium income (LKR)", "Approved claims (LKR)");
        for (MonthRow month : report.months()) {
            line(csv, month.month().toString(), money(month.premiumIncome()), money(month.approved()));
        }
        line(csv);
        line(csv, "Claim status", "Claims");
        report.claimsByStatus().forEach((status, count) -> line(csv, status.name(), String.valueOf(count)));
        return csv.toString();
    }

    private void planLine(StringBuilder csv, PlanRow row) {
        line(csv, row.planName(), String.valueOf(row.policiesInForce()), String.valueOf(row.payments()),
                money(row.premiumIncome()), String.valueOf(row.claims()), money(row.claimed()),
                String.valueOf(row.approvedClaims()), money(row.approved()),
                row.claimsRatio() == null ? "" : row.claimsRatio().toPlainString());
    }

    private static String money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static void line(StringBuilder csv, String... cells) {
        StringJoiner joined = new StringJoiner(",");
        for (String cell : cells) {
            joined.add(csvCell(cell));
        }
        csv.append(joined).append("\r\n");
    }

    /**
     * Quotes a cell when it contains a comma, quote or line break. Text starting with = + - or @
     * is prefixed with an apostrophe so a spreadsheet shows it as text instead of running it as a
     * formula (CSV injection); plain numbers are left alone.
     */
    static String csvCell(String value) {
        if (value == null) {
            return "";
        }
        String cell = value;
        if (!cell.isEmpty() && "=+-@".indexOf(cell.charAt(0)) >= 0 && !cell.matches("-?\\d+(\\.\\d+)?")) {
            cell = "'" + cell;
        }
        if (cell.contains(",") || cell.contains("\"") || cell.contains("\n") || cell.contains("\r")) {
            cell = "\"" + cell.replace("\"", "\"\"") + "\"";
        }
        return cell;
    }
}
