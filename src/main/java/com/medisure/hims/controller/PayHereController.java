package com.medisure.hims.controller;

import com.medisure.hims.config.UserPrincipal;
import com.medisure.hims.model.PayHereCheckout;
import com.medisure.hims.model.Policy;
import com.medisure.hims.service.PayHereService;
import com.medisure.hims.service.PolicyService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

/** Paying a premium online through PayHere. */
@Controller
@RequestMapping("/payments/payhere")
public class PayHereController {

    private final PayHereService payHereService;
    private final PolicyService policyService;

    public PayHereController(PayHereService payHereService, PolicyService policyService) {
        this.payHereService = payHereService;
        this.policyService = policyService;
    }

    /** Creates the checkout and shows a page that posts the signed request to PayHere. */
    @PostMapping("/checkout")
    public String checkout(@RequestParam Long policyId, @AuthenticationPrincipal UserPrincipal principal,
                           Model model, RedirectAttributes redirect) {
        Policy policy = policyService.findById(policyId);
        policyService.assertVisible(policy, principal.getUser());
        PayHereCheckout checkout;
        try {
            checkout = payHereService.start(policy, principal.getUser());
        } catch (IllegalStateException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/payments/new?policyId=" + policyId;
        }
        model.addAttribute("checkout", checkout);
        model.addAttribute("checkoutUrl", payHereService.checkoutUrl());
        model.addAttribute("fields", payHereService.checkoutFields(checkout));
        return "payments/payhere-redirect";
    }

    /**
     * The member is sent back here after paying. PayHere may add its own order_id to the link, so
     * the parameter can arrive twice; the first one is ours.
     */
    @GetMapping("/return")
    public String returned(@RequestParam("order_id") String[] orderIds, @AuthenticationPrincipal UserPrincipal principal,
                           Model model) {
        PayHereCheckout checkout = payHereService.confirmOnReturn(orderIds[0], principal.getUser());
        if (PayHereCheckout.COMPLETED.equals(checkout.getStatus()) && checkout.getPayment() != null) {
            return "redirect:/payments/" + checkout.getPayment().getId() + "?paid";
        }
        model.addAttribute("checkout", checkout);
        return "payments/payhere-status";
    }

    @GetMapping("/cancel")
    public String cancelled(@RequestParam("order_id") String[] orderIds, @AuthenticationPrincipal UserPrincipal principal,
                            RedirectAttributes redirect) {
        PayHereCheckout checkout = payHereService.cancel(orderIds[0], principal.getUser());
        redirect.addFlashAttribute("errorMessage", "The online payment was cancelled. No money was taken.");
        return "redirect:/payments/new?policyId=" + checkout.getPolicy().getId();
    }

    /** PayHere's server-to-server notification. Public, and trusted only after the signature check. */
    @PostMapping("/notify")
    @ResponseBody
    public ResponseEntity<String> notifyPayment(@RequestParam Map<String, String> params) {
        boolean accepted = payHereService.handleNotification(params);
        return accepted ? ResponseEntity.ok("OK") : ResponseEntity.badRequest().body("Rejected");
    }
}
