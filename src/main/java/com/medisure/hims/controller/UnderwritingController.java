package com.medisure.hims.controller;

import com.medisure.hims.config.UserPrincipal;
import com.medisure.hims.model.*;
import com.medisure.hims.service.PlanService;
import com.medisure.hims.service.UnderwritingService;
import com.medisure.hims.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

/**
 * Underwriting pages. The application is exposed to views as "underwritingApp", never
 * "application": Thymeleaf reserves ${application} for the servlet context, so a model
 * attribute with that name always resolves to null in templates.
 */
@Controller
@RequestMapping("/underwriting")
public class UnderwritingController {

    private final UnderwritingService underwritingService;
    private final PlanService planService;
    private final UserService userService;

    public UnderwritingController(UnderwritingService underwritingService, PlanService planService,
                                   UserService userService) {
        this.underwritingService = underwritingService;
        this.planService = planService;
        this.userService = userService;
    }

    @GetMapping
    public String list(@AuthenticationPrincipal UserPrincipal principal, Model model) {
        model.addAttribute("applications", underwritingService.findAllFor(principal.getUser()));
        return "underwriting/list";
    }

    @GetMapping("/new")
    public String newForm(@AuthenticationPrincipal UserPrincipal principal, Model model) {
        addFormOptions(principal.getUser(), model);
        return "underwriting/form";
    }

    @PostMapping
    public String submit(@AuthenticationPrincipal UserPrincipal principal,
                          @RequestParam(required = false) String applicantNic,
                          @RequestParam Long planId,
                          @RequestParam(defaultValue = "false") boolean hasPreExistingConditions,
                          @RequestParam(required = false) String conditionsNotes,
                          @RequestParam(defaultValue = "0") int numDependentsPlanned,
                          Model model) {
        User actor = principal.getUser();
        try {
            User applicant = actor;
            if (actor.getRole() != Role.POLICYHOLDER && applicantNic != null && !applicantNic.isBlank()) {
                applicant = findApplicant(applicantNic.trim());
            }
            InsurancePlan plan = planService.findById(planId);
            underwritingService.submit(applicant, plan, hasPreExistingConditions, conditionsNotes,
                    numDependentsPlanned, actor);
        } catch (IllegalStateException ex) {
            addFormOptions(actor, model);
            model.addAttribute("errorMessage", ex.getMessage());
            return "underwriting/form";
        }
        return "redirect:/underwriting";
    }

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal, Model model) {
        UnderwritingApplication app = underwritingService.findById(id);
        underwritingService.assertVisible(app, principal.getUser());
        model.addAttribute("underwritingApp", app);
        return "underwriting/view";
    }

    @GetMapping("/{id}/decide")
    public String decideForm(@PathVariable Long id, Model model) {
        UnderwritingApplication app = underwritingService.findById(id);
        if (app.getDecision() != ApplicationDecision.PENDING) {
            return "redirect:/underwriting/" + id;
        }
        model.addAttribute("underwritingApp", app);
        return "underwriting/decide";
    }

    @PostMapping("/{id}/decide")
    public String decide(@PathVariable Long id, @RequestParam ApplicationDecision decision,
                          @RequestParam(required = false) Integer riskScore,
                          @RequestParam(required = false) BigDecimal premiumLoadingPercent,
                          @AuthenticationPrincipal UserPrincipal principal, Model model) {
        try {
            underwritingService.decide(id, decision, riskScore, premiumLoadingPercent, principal.getUser());
        } catch (IllegalStateException ex) {
            model.addAttribute("underwritingApp", underwritingService.findById(id));
            model.addAttribute("errorMessage", ex.getMessage());
            return "underwriting/decide";
        }
        return "redirect:/underwriting/" + id;
    }

    private void addFormOptions(User actor, Model model) {
        model.addAttribute("plans", planService.findActive());
        model.addAttribute("isAgent", actor.getRole() != Role.POLICYHOLDER);
    }

    private User findApplicant(String nic) {
        try {
            return userService.findByNic(nic);
        } catch (ResponseStatusException ex) {
            throw new IllegalStateException("No registered member has the NIC " + nic);
        }
    }
}
