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

    public PaymentService(PaymentRepository paymentRepository, PolicyRepository policyRepository,
                           AuditService auditService) {
        this.paymentRepository = paymentRepository;
        this.policyRepository = policyRepository;
        this.auditService = auditService;
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

    public void cancelOrRefund(Long id, PaymentStatus newStatus, User actor) {
        Payment payment = findById(id);
        assertOwner(payment, actor);
        payment.setStatus(newStatus);
        paymentRepository.save(payment);
        auditService.log("Payment", payment.getId(), newStatus.name(), actor, null);
    }
}
