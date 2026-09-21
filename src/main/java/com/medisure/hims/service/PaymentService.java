package com.medisure.hims.service;

import com.medisure.hims.model.*;
import com.medisure.hims.repository.PaymentRepository;
import com.medisure.hims.repository.PolicyRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PolicyRepository policyRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;

    public PaymentService(PaymentRepository paymentRepository, PolicyRepository policyRepository,
                           AuditService auditService, NotificationService notificationService) {
        this.paymentRepository = paymentRepository;
        this.policyRepository = policyRepository;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    public List<Payment> findAllFor(User currentUser) {
        if (currentUser.getRole() == Role.POLICYHOLDER) {
            return paymentRepository.findByPolicyPolicyholder(currentUser);
        }
        return paymentRepository.findAll();
    }

    public Payment findById(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Payment not found"));
    }

    public void assertOwner(Payment payment, User currentUser) {
        if (currentUser.getRole() == Role.POLICYHOLDER
                && !payment.getPolicy().getPolicyholder().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You may not access this payment");
        }
    }

    public Payment makePayment(Policy policy, BigDecimal amount, PaymentMethod method, boolean autoPay, User actor) {
        LocalDate periodStart = policy.getNextDueDate();
        LocalDate periodEnd = switch (policy.getPremiumFrequency()) {
            case MONTHLY -> periodStart.plusMonths(1);
            case QUARTERLY -> periodStart.plusMonths(3);
            case ANNUAL -> periodStart.plusYears(1);
        };

        Payment payment = new Payment();
        payment.setPolicy(policy);
        payment.setAmount(amount);
        payment.setMethod(method);
        payment.setAutoPay(autoPay);
        payment.setStatus(PaymentStatus.PAID);
        payment.setBillingPeriodStart(periodStart);
        payment.setBillingPeriodEnd(periodEnd);
        payment.setPaidAt(LocalDateTime.now());
        Payment saved = paymentRepository.save(payment);

        policy.setNextDueDate(periodEnd);
        policyRepository.save(policy);

        auditService.log("Payment", saved.getId(), "PAID", actor, amount + " for " + policy.getPolicyCode());
        return saved;
    }

    public Payment updateMethod(Long id, PaymentMethod method, boolean autoPay, User actor) {
        Payment payment = findById(id);
        assertOwner(payment, actor);
        payment.setMethod(method);
        payment.setAutoPay(autoPay);
        Payment saved = paymentRepository.save(payment);
        auditService.log("Payment", saved.getId(), "METHOD_UPDATED", actor, method.name());
        return saved;
    }

    public List<Payment> pendingRefunds() {
        return paymentRepository.findByRefundRequestedAtIsNotNullAndRefundDecidedAtIsNull();
    }

    /**
     * A policyholder asks for their money back. Nothing is refunded yet — the payment stays PAID
     * until an admin or claims officer approves the request in {@link #decideRefund}.
     */
    public Payment requestRefund(Long id, String reason, User actor) {
        Payment payment = findById(id);
        if (!payment.getPolicy().getPolicyholder().getId().equals(actor.getId())) {
            throw new AccessDeniedException("Only the policyholder can request a refund on this payment");
        }
        if (payment.getStatus() != PaymentStatus.PAID) {
            throw new IllegalStateException("Only a paid payment can be refunded");
        }
        if (payment.isRefundPending()) {
            throw new IllegalStateException("A refund has already been requested for this payment");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalStateException("Please give a reason for the refund");
        }
        payment.setRefundRequestedAt(LocalDateTime.now());
        payment.setRefundReason(reason.trim());
        payment.setRefundDecidedBy(null);
        payment.setRefundDecidedAt(null);
        payment.setRefundDecisionNotes(null);
        Payment saved = paymentRepository.save(payment);
        auditService.log("Payment", saved.getId(), "REFUND_REQUESTED", actor, saved.getRefundReason());
        String title = "Refund request on " + saved.getPolicy().getPolicyCode();
        String message = "LKR " + saved.getAmount() + ": " + saved.getRefundReason();
        notificationService.notifyRole(Role.CLAIMS_OFFICER, title, message, "/payments/" + saved.getId());
        notificationService.notifyRole(Role.ADMIN, title, message, "/payments/" + saved.getId());
        return saved;
    }

    /** An admin or claims officer approves (status becomes REFUNDED) or rejects a pending request. */
    public Payment decideRefund(Long id, boolean approve, String notes, User actor) {
        assertStaff(actor, "Only an admin or claims officer can decide a refund");
        Payment payment = findById(id);
        if (!payment.isRefundPending()) {
            throw new IllegalStateException("There is no pending refund request on this payment");
        }
        if (!approve && (notes == null || notes.isBlank())) {
            throw new IllegalStateException("Please give a reason for rejecting the refund");
        }
        if (approve) {
            payment.setStatus(PaymentStatus.REFUNDED);
        }
        payment.setRefundDecidedBy(actor);
        payment.setRefundDecidedAt(LocalDateTime.now());
        payment.setRefundDecisionNotes(notes == null || notes.isBlank() ? null : notes.trim());
        Payment saved = paymentRepository.save(payment);
        auditService.log("Payment", saved.getId(), approve ? "REFUND_APPROVED" : "REFUND_REJECTED", actor,
                saved.getRefundDecisionNotes());
        notificationService.notify(saved.getPolicy().getPolicyholder(),
                "Refund " + (approve ? "approved" : "not approved") + " for " + saved.getPolicy().getPolicyCode(),
                saved.getRefundDecisionNotes() != null ? saved.getRefundDecisionNotes()
                        : "LKR " + saved.getAmount() + " will be returned to you.",
                "/payments/" + saved.getId());
        return saved;
    }

    /** Staff void a payment that was recorded in error, such as a duplicate entry. */
    public Payment voidPayment(Long id, String reason, User actor) {
        assertStaff(actor, "Only an admin or claims officer can void a payment");
        Payment payment = findById(id);
        if (payment.getStatus() != PaymentStatus.PAID) {
            throw new IllegalStateException("Only a paid payment can be voided");
        }
        if (payment.isRefundPending()) {
            throw new IllegalStateException("Decide the pending refund request before voiding this payment");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalStateException("Please give a reason for voiding the payment");
        }
        payment.setStatus(PaymentStatus.CANCELLED);
        payment.setVoidReason(reason.trim());
        Payment saved = paymentRepository.save(payment);
        auditService.log("Payment", saved.getId(), "VOIDED", actor, saved.getVoidReason());
        return saved;
    }

    private void assertStaff(User actor, String message) {
        if (actor.getRole() != Role.ADMIN && actor.getRole() != Role.CLAIMS_OFFICER) {
            throw new AccessDeniedException(message);
        }
    }
}
