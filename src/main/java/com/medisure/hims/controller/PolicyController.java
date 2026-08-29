package com.medisure.hims.controller;

import com.medisure.hims.config.UserPrincipal;
import com.medisure.hims.model.*;
import com.medisure.hims.service.ClaimService;
import com.medisure.hims.service.PaymentService;
import com.medisure.hims.service.PolicyService;
import com.medisure.hims.service.UnderwritingService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Controller
@RequestMapping("/policies")
public class PolicyController {

    private final PolicyService policyService;
    private final UnderwritingService underwritingService;
    private final ClaimService claimService;
    private final PaymentService paymentService;

    public PolicyController(PolicyService policyService, UnderwritingService underwritingService,
                             ClaimService claimService, PaymentService paymentService) {
        this.policyService = policyService;
        this.underwritingService = underwritingService;
        this.claimService = claimService;
        this.paymentService = paymentService;
    }

    @GetMapping
    public String list(@AuthenticationPrincipal UserPrincipal principal, Model model) {
        model.addAttribute("policies", policyService.findAllFor(principal.getUser()));
        return "policies/list";
    }

    @GetMapping("/issue/{applicationId}")
    public String issueForm(@PathVariable Long applicationId, Model model) {
        model.addAttribute("application", underwritingService.findById(applicationId));
        model.addAttribute("frequencies", PremiumFrequency.values());
        return "policies/issue";
    }

    @PostMapping("/issue/{applicationId}")
    public String issue(@PathVariable Long applicationId, @RequestParam PremiumFrequency premiumFrequency,
                         @AuthenticationPrincipal UserPrincipal principal, Model model) {
        UnderwritingApplication app = underwritingService.findById(applicationId);
        try {
            Policy policy = policyService.issueFromApplication(app, premiumFrequency, principal.getUser());
            return "redirect:/policies/" + policy.getId();
        } catch (IllegalStateException ex) {
            model.addAttribute("application", app);
            model.addAttribute("frequencies", PremiumFrequency.values());
            model.addAttribute("errorMessage", ex.getMessage());
            return "policies/issue";
        }
    }

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal, Model model) {
        Policy policy = policyService.findById(id);
        policyService.assertVisible(policy, principal.getUser());
        model.addAttribute("policy", policy);
        model.addAttribute("claims", claimService.findAllFor(principal.getUser()).stream()
                .filter(c -> c.getPolicy().getId().equals(id)).toList());
        model.addAttribute("payments", paymentService.findAllFor(principal.getUser()).stream()
                .filter(p -> p.getPolicy().getId().equals(id)).toList());
        return "policies/view";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("policy", policyService.findById(id));
        model.addAttribute("frequencies", PremiumFrequency.values());
        return "policies/edit";
    }

    @PostMapping("/{id}")
    public String renew(@PathVariable Long id, @RequestParam LocalDate newEndDate,
                         @RequestParam PremiumFrequency premiumFrequency,
                         @AuthenticationPrincipal UserPrincipal principal) {
        policyService.renewOrModify(id, newEndDate, premiumFrequency, principal.getUser());
        return "redirect:/policies/" + id;
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        policyService.cancel(id, principal.getUser());
        return "redirect:/policies/" + id;
    }

    @PostMapping("/{id}/dependents")
    public String addDependent(@PathVariable Long id, @ModelAttribute Dependent dependent,
                                @AuthenticationPrincipal UserPrincipal principal, Model model) {
        try {
            policyService.addDependent(id, dependent, principal.getUser());
        } catch (IllegalStateException ex) {
            model.addAttribute("policy", policyService.findById(id));
            model.addAttribute("errorMessage", ex.getMessage());
            return "policies/view";
        }
        return "redirect:/policies/" + id;
    }

    @PostMapping("/{id}/dependents/{dependentId}/delete")
    public String removeDependent(@PathVariable Long id, @PathVariable Long dependentId,
                                   @AuthenticationPrincipal UserPrincipal principal) {
        policyService.removeDependent(dependentId, principal.getUser());
        return "redirect:/policies/" + id;
    }
}
