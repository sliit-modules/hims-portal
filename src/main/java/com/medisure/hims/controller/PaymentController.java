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
    public String list(@AuthenticationPrincipal UserPrincipal principal, Model model) {
        model.addAttribute("payments", paymentService.findAllFor(principal.getUser()));
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

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        paymentService.cancelOrRefund(id, PaymentStatus.CANCELLED, principal.getUser());
        return "redirect:/payments/" + id;
    }

    @PostMapping("/{id}/refund")
    public String refund(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        paymentService.cancelOrRefund(id, PaymentStatus.REFUNDED, principal.getUser());
        return "redirect:/payments/" + id;
    }
}
