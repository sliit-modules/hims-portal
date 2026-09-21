package com.medisure.hims.controller;

import com.medisure.hims.config.UserPrincipal;
import com.medisure.hims.model.*;
import com.medisure.hims.service.AuditService;
import com.medisure.hims.service.ClaimService;
import com.medisure.hims.service.PaymentService;
import com.medisure.hims.service.PolicyService;
import com.medisure.hims.service.UnderwritingService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Controller
@RequestMapping("/policies")
public class PolicyController {

    private final PolicyService policyService;
    private final UnderwritingService underwritingService;
    private final ClaimService claimService;
    private final PaymentService paymentService;
    private final AuditService auditService;

    public PolicyController(PolicyService policyService, UnderwritingService underwritingService,
                             ClaimService claimService, PaymentService paymentService,
                             AuditService auditService) {
        this.policyService = policyService;
        this.underwritingService = underwritingService;
        this.claimService = claimService;
        this.paymentService = paymentService;
        this.auditService = auditService;
    }

    /**
     * Add Dependent binds the form onto a Dependent at /policies/{id}/dependents. Without this,
     * Spring also binds the URL's {id} (the policy id) onto Dependent.id, so saving would
     * overwrite whichever existing dependent happens to share that number.
     */
    @InitBinder("dependent")
    void protectDependentFields(WebDataBinder binder) {
        binder.setDisallowedFields("id", "policy", "consentConfirmedAt");
    }

    @GetMapping
    public String list(@AuthenticationPrincipal UserPrincipal principal, Model model) {
        model.addAttribute("policies", policyService.findAllFor(principal.getUser()));
        return "policies/list";
    }

    @GetMapping("/issue/{applicationId}")
    public String issueForm(@PathVariable Long applicationId, Model model) {
        model.addAttribute("underwritingApp", underwritingService.findById(applicationId));
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
            model.addAttribute("underwritingApp", app);
            model.addAttribute("frequencies", PremiumFrequency.values());
            model.addAttribute("errorMessage", ex.getMessage());
            return "policies/issue";
        }
    }

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal, Model model) {
        Policy policy = policyService.findById(id);
        policyService.assertVisible(policy, principal.getUser());
        // The policy page shows the policyholder's details and every dependent's medical profile.
        auditService.logAccess(policy.getPolicyholder(), principal.getUser(), AuditService.VIEWED_POLICY,
                "Policy " + policy.getPolicyCode() + " (policyholder and "
                        + policy.getDependents().size() + " dependent(s))");
        model.addAttribute("policy", policy);
        model.addAttribute("claims", claimService.findAllFor(principal.getUser()).stream()
                .filter(c -> c.getPolicy().getId().equals(id)).toList());
        model.addAttribute("payments", paymentService.findAllFor(principal.getUser()).stream()
                .filter(p -> p.getPolicy().getId().equals(id)).toList());
        return "policies/view";
    }

    /** A printable certificate of insurance; the browser's print dialog can save it as a PDF. */
    @GetMapping("/{id}/certificate")
    public String certificate(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal, Model model) {
        Policy policy = policyService.findById(id);
        policyService.assertVisible(policy, principal.getUser());
        // The certificate shows the holder's NIC and date of birth, so staff opening it is recorded.
        auditService.logAccess(policy.getPolicyholder(), principal.getUser(), AuditService.VIEWED_POLICY,
                "Certificate for policy " + policy.getPolicyCode());
        model.addAttribute("policy", policy);
        model.addAttribute("inForce", policy.getStatus() == PolicyStatus.ACTIVE || policy.getStatus() == PolicyStatus.RENEWED);
        model.addAttribute("printedOn", LocalDate.now());
        return "policies/certificate";
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
