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

import java.math.BigDecimal;

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
        model.addAttribute("plans", planService.findActive());
        model.addAttribute("isAgent", principal.getUser().getRole() != Role.POLICYHOLDER);
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
        User applicant = actor;
        if (actor.getRole() != Role.POLICYHOLDER && applicantNic != null && !applicantNic.isBlank()) {
            applicant = userService.findByNic(applicantNic);
        }
        InsurancePlan plan = planService.findById(planId);
        underwritingService.submit(applicant, plan, hasPreExistingConditions, conditionsNotes,
                numDependentsPlanned, actor);
        return "redirect:/underwriting";
    }

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal, Model model) {
        UnderwritingApplication app = underwritingService.findById(id);
        underwritingService.assertVisible(app, principal.getUser());
        model.addAttribute("application", app);
        return "underwriting/view";
    }

    @GetMapping("/{id}/decide")
    public String decideForm(@PathVariable Long id, Model model) {
        model.addAttribute("application", underwritingService.findById(id));
        return "underwriting/decide";
    }

    @PostMapping("/{id}/decide")
    public String decide(@PathVariable Long id, @RequestParam ApplicationDecision decision,
                          @RequestParam int riskScore, @RequestParam BigDecimal premiumLoadingPercent,
                          @AuthenticationPrincipal UserPrincipal principal) {
        underwritingService.decide(id, decision, riskScore, premiumLoadingPercent, principal.getUser());
        return "redirect:/underwriting/" + id;
    }
}
