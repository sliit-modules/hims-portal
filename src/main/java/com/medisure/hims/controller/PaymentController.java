package com.medisure.hims.controller;

import com.medisure.hims.config.UserPrincipal;
import com.medisure.hims.model.*;
import com.medisure.hims.service.PaymentService;
import com.medisure.hims.service.PolicyService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@Controller
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final PolicyService policyService;

    public PaymentController(PaymentService paymentService, PolicyService policyService) {
        this.paymentService = paymentService;
        this.policyService = policyService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String refunds,
                       @AuthenticationPrincipal UserPrincipal principal, Model model) {
        User user = principal.getUser();
        boolean reviewer = user.getRole() == Role.ADMIN || user.getRole() == Role.CLAIMS_OFFICER;
        boolean pendingOnly = reviewer && "pending".equals(refunds);
        List<Payment> payments = pendingOnly ? paymentService.pendingRefunds() : paymentService.findAllFor(user);
        model.addAttribute("payments", payments);
        model.addAttribute("pendingRefundFilter", pendingOnly);
        model.addAttribute("pendingRefundCount", reviewer ? paymentService.pendingRefunds().size() : 0);
        return "payments/list";
    }

    @GetMapping("/new")
    public String newForm(@RequestParam Long policyId, @AuthenticationPrincipal UserPrincipal principal,
                           Model model) {
        Policy policy = policyService.findById(policyId);
        policyService.assertVisible(policy, principal.getUser());
        model.addAttribute("policy", policy);
        model.addAttribute("methods", PaymentMethod.values());
        return "payments/form";
    }

    @PostMapping
    public String create(@RequestParam Long policyId, @RequestParam BigDecimal amount,
                          @RequestParam PaymentMethod method, @RequestParam(defaultValue = "false") boolean autoPay,
                          @AuthenticationPrincipal UserPrincipal principal) {
        Policy policy = policyService.findById(policyId);
        policyService.assertVisible(policy, principal.getUser());
        Payment payment = paymentService.makePayment(policy, amount, method, autoPay, principal.getUser());
        return "redirect:/payments/" + payment.getId();
    }

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal, Model model) {
        Payment payment = paymentService.findById(id);
        paymentService.assertOwner(payment, principal.getUser());
        model.addAttribute("payment", payment);
        model.addAttribute("methods", PaymentMethod.values());
        return "payments/view";
    }

    @PostMapping("/{id}/method")
    public String updateMethod(@PathVariable Long id, @RequestParam PaymentMethod method,
                                @RequestParam(defaultValue = "false") boolean autoPay,
                                @AuthenticationPrincipal UserPrincipal principal) {
        paymentService.updateMethod(id, method, autoPay, principal.getUser());
        return "redirect:/payments/" + id;
    }

    @PostMapping("/{id}/refund-request")
    public String requestRefund(@PathVariable Long id, @RequestParam(required = false) String reason,
                                 @AuthenticationPrincipal UserPrincipal principal, Model model) {
        try {
            paymentService.requestRefund(id, reason, principal.getUser());
        } catch (IllegalStateException ex) {
            return showWithError(id, ex, model);
        }
        return "redirect:/payments/" + id;
    }

    @PostMapping("/{id}/refund-decision")
    public String decideRefund(@PathVariable Long id, @RequestParam boolean approve,
                                @RequestParam(required = false) String notes,
                                @AuthenticationPrincipal UserPrincipal principal, Model model) {
        try {
            paymentService.decideRefund(id, approve, notes, principal.getUser());
        } catch (IllegalStateException ex) {
            return showWithError(id, ex, model);
        }
        return "redirect:/payments/" + id;
    }

    @PostMapping("/{id}/void")
    public String voidPayment(@PathVariable Long id, @RequestParam(required = false) String reason,
                               @AuthenticationPrincipal UserPrincipal principal, Model model) {
        try {
            paymentService.voidPayment(id, reason, principal.getUser());
        } catch (IllegalStateException ex) {
            return showWithError(id, ex, model);
        }
        return "redirect:/payments/" + id;
    }

    private String showWithError(Long id, IllegalStateException ex, Model model) {
        model.addAttribute("payment", paymentService.findById(id));
        model.addAttribute("methods", PaymentMethod.values());
        model.addAttribute("errorMessage", ex.getMessage());
        return "payments/view";
    }
}
