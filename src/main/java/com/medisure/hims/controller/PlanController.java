package com.medisure.hims.controller;

import com.medisure.hims.config.UserPrincipal;
import com.medisure.hims.model.InsurancePlan;
import com.medisure.hims.service.PlanService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/plans")
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("plans", planService.findAll());
        return "plans/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        if (!model.containsAttribute("plan")) {
            model.addAttribute("plan", new InsurancePlan());
        }
        return "plans/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("plan") InsurancePlan plan, BindingResult result,
                          @AuthenticationPrincipal UserPrincipal principal) {
        if (result.hasErrors()) {
            return "plans/form";
        }
        planService.create(plan, principal.getUser());
        return "redirect:/plans";
    }

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model) {
        model.addAttribute("plan", planService.findById(id));
        return "plans/view";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        if (!model.containsAttribute("plan")) {
            model.addAttribute("plan", planService.findById(id));
        }
        model.addAttribute("planId", id);
        return "plans/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("plan") InsurancePlan plan,
                          BindingResult result, @AuthenticationPrincipal UserPrincipal principal, Model model) {
        if (result.hasErrors()) {
            model.addAttribute("planId", id);
            return "plans/form";
        }
        planService.update(id, plan, principal.getUser());
        return "redirect:/plans/" + id;
    }

    @PostMapping("/{id}/delete")
    public String discontinue(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        planService.discontinue(id, principal.getUser());
        return "redirect:/plans";
    }
}
