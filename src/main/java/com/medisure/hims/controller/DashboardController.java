package com.medisure.hims.controller;

import com.medisure.hims.config.UserPrincipal;
import com.medisure.hims.model.*;
import com.medisure.hims.repository.AuditLogRepository;
import com.medisure.hims.service.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Controller
public class DashboardController {

    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM");

    private final PlanService planService;
    private final UnderwritingService underwritingService;
    private final PolicyService policyService;
    private final ClaimService claimService;
    private final PaymentService paymentService;
    private final TicketService ticketService;
    private final UserService userService;
    private final AuditLogRepository auditLogRepository;

    public DashboardController(PlanService planService, UnderwritingService underwritingService,
                                PolicyService policyService, ClaimService claimService,
                                PaymentService paymentService, TicketService ticketService,
                                UserService userService, AuditLogRepository auditLogRepository) {
        this.planService = planService;
        this.underwritingService = underwritingService;
        this.policyService = policyService;
        this.claimService = claimService;
        this.paymentService = paymentService;
        this.ticketService = ticketService;
        this.userService = userService;
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping("/")
    public String root() {
        return "redirect:/dashboard";
    }

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal UserPrincipal principal, Model model) {
        User user = principal.getUser();
        model.addAttribute("user", user);
        model.addAttribute("monthLabels", monthLabels());

        switch (user.getRole()) {
            case ADMIN -> adminDashboard(user, model);
            case SALES_AGENT -> salesAgentDashboard(user, model);
            case UNDERWRITER -> underwriterDashboard(user, model);
            case CLAIMS_OFFICER -> claimsOfficerDashboard(user, model);
            case POLICYHOLDER -> policyholderDashboard(user, model);
            case CRE -> creDashboard(user, model);
        }
        return "dashboard/home";
    }

    // ------------------------------------------------------------------ per-role

    private void adminDashboard(User user, Model model) {
        List<Policy> policies = policyService.findAllFor(user);
        List<Claim> claims = claimService.findAllFor(user);
        List<Payment> payments = paymentService.findAllFor(user);
        List<SupportTicket> tickets = ticketService.findAllFor(user);

        addClaimStatusCounts(model, claims);
        model.addAttribute("totalClaims", claims.size());
        model.addAttribute("planCount", planService.findAll().size());
        model.addAttribute("policyCount", policies.size());
        model.addAttribute("policyholderCount", userService.findByRole(Role.POLICYHOLDER).size());
        model.addAttribute("openTickets", tickets.stream().filter(t -> t.getStatus() != TicketStatus.CLOSED).count());

        BigDecimal collected = payments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.PAID)
                .map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("totalCollected", collected);

        BigDecimal paidOut = claims.stream()
                .filter(c -> c.getStatus() == ClaimStatus.APPROVED)
                .map(Claim::getAmountClaimed).reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("totalPaidOut", paidOut);

        long decided = claims.stream().filter(c -> c.getStatus() == ClaimStatus.APPROVED
                || c.getStatus() == ClaimStatus.REJECTED).count();
        long approved = claims.stream().filter(c -> c.getStatus() == ClaimStatus.APPROVED).count();
        model.addAttribute("approvalRate", percent(approved, decided));

        long onTime = policies.stream().filter(p -> !p.getNextDueDate().isBefore(java.time.LocalDate.now())).count();
        model.addAttribute("collectionRate", percent(onTime, policies.size()));

        model.addAttribute("claimsByMonth", bucketByMonth(claims, Claim::getSubmittedAt));
        model.addAttribute("premiumsByMonth", sumByMonth(payments, Payment::getPaidAt, Payment::getAmount));
        addCategoryBreakdown(model, claims);

        model.addAttribute("recentClaims", claims.stream()
                .sorted(Comparator.comparing(Claim::getSubmittedAt).reversed()).limit(5).toList());
        // Changes only: record views (VIEWED_*) are shown on the Audit Log's "Record access" tab.
        model.addAttribute("recentActivity", auditLogRepository.findByActionNotLikeOrderByTimestampDesc("VIEWED_%")
                .stream().limit(5).toList());
    }

    private void salesAgentDashboard(User user, Model model) {
        List<UnderwritingApplication> apps = underwritingService.findAllFor(user);
        List<Policy> policies = policyService.findAllFor(user);
        List<Payment> payments = paymentService.findAllFor(user);

        List<UnderwritingApplication> readyToIssue = apps.stream()
                .filter(a -> a.getDecision() == ApplicationDecision.APPROVED)
                .filter(a -> !policyService.existsForApplication(a.getId()))
                .sorted(Comparator.comparing(UnderwritingApplication::getDecidedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        model.addAttribute("activePlans", planService.findActive().size());
        model.addAttribute("readyToIssue", readyToIssue);
        model.addAttribute("policyCount", policies.size());
        model.addAttribute("policyholderCount", userService.findByRole(Role.POLICYHOLDER).size());

        BigDecimal bookValue = policies.stream()
                .filter(p -> p.getStatus() == PolicyStatus.ACTIVE || p.getStatus() == PolicyStatus.RENEWED)
                .map(Policy::getPremiumAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("bookValue", bookValue);

        long active = policies.stream().filter(p -> p.getStatus() == PolicyStatus.ACTIVE
                || p.getStatus() == PolicyStatus.RENEWED).count();
        model.addAttribute("activeRate", percent(active, policies.size()));
        long approvedApps = apps.stream().filter(a -> a.getDecision() == ApplicationDecision.APPROVED).count();
        long decidedApps = apps.stream().filter(a -> a.getDecision() != ApplicationDecision.PENDING).count();
        model.addAttribute("conversionRate", percent(approvedApps, decidedApps));

        model.addAttribute("policiesByMonth", bucketByMonth(policies,
                p -> p.getStartDate().atStartOfDay()));
        model.addAttribute("premiumsByMonth", sumByMonth(payments, Payment::getPaidAt, Payment::getAmount));

        Map<String, Long> planMix = new LinkedHashMap<>();
        for (Policy p : policies) {
            planMix.merge(p.getPlan().getPlanName(), 1L, Long::sum);
        }
        model.addAttribute("planMixLabels", new ArrayList<>(planMix.keySet()));
        model.addAttribute("planMixValues", new ArrayList<>(planMix.values()));

        model.addAttribute("recentPolicies", policies.stream()
                .sorted(Comparator.comparing(Policy::getId).reversed()).limit(5).toList());
    }

    private void underwriterDashboard(User user, Model model) {
        List<UnderwritingApplication> apps = underwritingService.findAllFor(user);
        List<UnderwritingApplication> pending = apps.stream()
                .filter(a -> a.getDecision() == ApplicationDecision.PENDING)
                .sorted(Comparator.comparing(UnderwritingApplication::getSubmittedAt))
                .toList();

        long approved = apps.stream().filter(a -> a.getDecision() == ApplicationDecision.APPROVED).count();
        long rejected = apps.stream().filter(a -> a.getDecision() == ApplicationDecision.REJECTED).count();

        model.addAttribute("totalApplications", apps.size());
        model.addAttribute("pendingCount", (long) pending.size());
        model.addAttribute("approvedCount", approved);
        model.addAttribute("rejectedCount", rejected);
        model.addAttribute("pendingApplications", pending.stream().limit(5).toList());
        model.addAttribute("approvalRate", percent(approved, approved + rejected));

        List<UnderwritingApplication> scored = apps.stream().filter(a -> a.getRiskScore() != null).toList();
        double avgRisk = scored.stream().mapToInt(UnderwritingApplication::getRiskScore).average().orElse(0);
        model.addAttribute("averageRisk", BigDecimal.valueOf(avgRisk).setScale(0, RoundingMode.HALF_UP));

        model.addAttribute("applicationsByMonth", bucketByMonth(apps, UnderwritingApplication::getSubmittedAt));

        long low = scored.stream().filter(a -> a.getRiskScore() < 35).count();
        long medium = scored.stream().filter(a -> a.getRiskScore() >= 35 && a.getRiskScore() < 65).count();
        long high = scored.stream().filter(a -> a.getRiskScore() >= 65).count();
        model.addAttribute("riskLow", low);
        model.addAttribute("riskMedium", medium);
        model.addAttribute("riskHigh", high);

        model.addAttribute("recentDecisions", apps.stream()
                .filter(a -> a.getDecidedAt() != null)
                .sorted(Comparator.comparing(UnderwritingApplication::getDecidedAt).reversed())
                .limit(5).toList());
    }

    private void claimsOfficerDashboard(User user, Model model) {
        List<Claim> claims = claimService.findAllFor(user);
        List<Claim> submitted = claims.stream()
                .filter(c -> c.getStatus() == ClaimStatus.SUBMITTED)
                .sorted(Comparator.comparing(Claim::getSubmittedAt))
                .toList();

        addClaimStatusCounts(model, claims);
        addCategoryBreakdown(model, claims);
        model.addAttribute("totalClaims", claims.size());
        model.addAttribute("submittedCount", (long) submitted.size());
        model.addAttribute("submittedClaims", submitted.stream().limit(6).toList());

        BigDecimal pendingValue = submitted.stream()
                .map(Claim::getAmountClaimed).reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("pendingValue", pendingValue);

        BigDecimal paidOut = claims.stream()
                .filter(c -> c.getStatus() == ClaimStatus.APPROVED)
                .map(Claim::getAmountClaimed).reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("totalPaidOut", paidOut);

        long decided = claims.stream().filter(c -> c.getStatus() == ClaimStatus.APPROVED
                || c.getStatus() == ClaimStatus.REJECTED).count();
        long approved = claims.stream().filter(c -> c.getStatus() == ClaimStatus.APPROVED).count();
        model.addAttribute("approvalRate", percent(approved, decided));
        model.addAttribute("settledRate", percent(decided, claims.size()));

        model.addAttribute("claimsByMonth", bucketByMonth(claims, Claim::getSubmittedAt));
        model.addAttribute("claimValueByMonth", sumByMonth(claims, Claim::getSubmittedAt, Claim::getAmountClaimed));
        model.addAttribute("recentClaims", claims.stream()
                .sorted(Comparator.comparing(Claim::getSubmittedAt).reversed()).limit(5).toList());
    }

    private void policyholderDashboard(User user, Model model) {
        List<Policy> policies = policyService.findAllFor(user);
        List<Claim> claims = claimService.findAllFor(user);
        List<Payment> payments = paymentService.findAllFor(user);
        List<SupportTicket> tickets = ticketService.findAllFor(user);

        model.addAttribute("policies", policies);
        model.addAttribute("claimCount", claims.size());
        model.addAttribute("paymentCount", payments.size());
        model.addAttribute("openTicketCount", tickets.stream()
                .filter(t -> t.getStatus() != TicketStatus.CLOSED).count());
        addClaimStatusCounts(model, claims);
        addCategoryBreakdown(model, claims);

        BigDecimal totalPaid = payments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.PAID)
                .map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("totalPaid", totalPaid);

        BigDecimal reimbursed = claims.stream()
                .filter(c -> c.getStatus() == ClaimStatus.APPROVED)
                .map(Claim::getAmountClaimed).reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("totalReimbursed", reimbursed);

        BigDecimal totalLimit = policies.stream()
                .map(p -> p.getPlan().getCoverageLimit()).reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("totalCoverage", totalLimit);
        model.addAttribute("coverageUsedPercent", totalLimit.signum() == 0 ? BigDecimal.ZERO
                : reimbursed.divide(totalLimit, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP));

        model.addAttribute("claimsByMonth", bucketByMonth(claims, Claim::getSubmittedAt));
        model.addAttribute("premiumsByMonth", sumByMonth(payments, Payment::getPaidAt, Payment::getAmount));
        model.addAttribute("recentClaims", claims.stream()
                .sorted(Comparator.comparing(Claim::getSubmittedAt).reversed()).limit(5).toList());
        model.addAttribute("recentPayments", payments.stream()
                .sorted(Comparator.comparing(Payment::getPaidAt).reversed()).limit(4).toList());
    }

    private void creDashboard(User user, Model model) {
        List<SupportTicket> tickets = ticketService.findAllFor(user);
        List<SupportTicket> open = tickets.stream()
                .filter(t -> t.getStatus() != TicketStatus.CLOSED)
                .sorted(Comparator.comparing(SupportTicket::getCreatedAt))
                .toList();

        long openCount = tickets.stream().filter(t -> t.getStatus() == TicketStatus.OPEN).count();
        long inProgress = tickets.stream().filter(t -> t.getStatus() == TicketStatus.IN_PROGRESS).count();
        long closed = tickets.stream().filter(t -> t.getStatus() == TicketStatus.CLOSED).count();

        model.addAttribute("openCount", openCount);
        model.addAttribute("inProgressCount", inProgress);
        model.addAttribute("closedCount", closed);
        model.addAttribute("totalCount", tickets.size());
        model.addAttribute("openTicketsList", open.stream().limit(6).toList());
        model.addAttribute("resolutionRate", percent(closed, tickets.size()));
        model.addAttribute("respondedRate", percent(closed + inProgress, tickets.size()));
        model.addAttribute("ticketsByMonth", bucketByMonth(tickets, SupportTicket::getCreatedAt));
        model.addAttribute("recentTickets", tickets.stream()
                .sorted(Comparator.comparing(SupportTicket::getCreatedAt).reversed()).limit(5).toList());
    }

    // ------------------------------------------------------------------ helpers

    private void addClaimStatusCounts(Model model, List<Claim> claims) {
        model.addAttribute("claimsSubmitted", claims.stream().filter(c -> c.getStatus() == ClaimStatus.SUBMITTED).count());
        model.addAttribute("claimsApproved", claims.stream().filter(c -> c.getStatus() == ClaimStatus.APPROVED).count());
        model.addAttribute("claimsRejected", claims.stream().filter(c -> c.getStatus() == ClaimStatus.REJECTED).count());
        model.addAttribute("claimsWithdrawn", claims.stream().filter(c -> c.getStatus() == ClaimStatus.WITHDRAWN).count());
    }

    private void addCategoryBreakdown(Model model, List<Claim> claims) {
        Map<String, Long> byCategory = new LinkedHashMap<>();
        for (ClaimCategory category : ClaimCategory.values()) {
            long count = claims.stream().filter(c -> c.getCategory() == category).count();
            if (count > 0) {
                byCategory.put(category.getLabel(), count);
            }
        }
        model.addAttribute("categoryLabels", new ArrayList<>(byCategory.keySet()));
        model.addAttribute("categoryValues", new ArrayList<>(byCategory.values()));
    }

    private List<String> monthLabels() {
        List<String> labels = new ArrayList<>();
        YearMonth start = YearMonth.now().minusMonths(11);
        for (int i = 0; i < 12; i++) {
            labels.add(start.plusMonths(i).format(MONTH_LABEL));
        }
        return labels;
    }

    /** Counts items into the last 12 calendar months. */
    private <T> List<Long> bucketByMonth(List<T> items, Function<T, LocalDateTime> dateFn) {
        YearMonth start = YearMonth.now().minusMonths(11);
        List<Long> buckets = new ArrayList<>(java.util.Collections.nCopies(12, 0L));
        for (T item : items) {
            LocalDateTime date = dateFn.apply(item);
            if (date == null) continue;
            int index = (int) java.time.temporal.ChronoUnit.MONTHS.between(start, YearMonth.from(date));
            if (index >= 0 && index < 12) {
                buckets.set(index, buckets.get(index) + 1);
            }
        }
        return buckets;
    }

    /** Sums an amount into the last 12 calendar months. */
    private <T> List<BigDecimal> sumByMonth(List<T> items, Function<T, LocalDateTime> dateFn,
                                             Function<T, BigDecimal> amountFn) {
        YearMonth start = YearMonth.now().minusMonths(11);
        List<BigDecimal> buckets = new ArrayList<>(java.util.Collections.nCopies(12, BigDecimal.ZERO));
        for (T item : items) {
            LocalDateTime date = dateFn.apply(item);
            if (date == null) continue;
            int index = (int) java.time.temporal.ChronoUnit.MONTHS.between(start, YearMonth.from(date));
            if (index >= 0 && index < 12) {
                buckets.set(index, buckets.get(index).add(amountFn.apply(item)));
            }
        }
        return buckets;
    }

    private BigDecimal percent(long part, long total) {
        if (total == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(part)
                .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);
    }
}
