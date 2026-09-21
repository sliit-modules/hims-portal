package com.medisure.hims.controller;

import com.medisure.hims.config.UserPrincipal;
import com.medisure.hims.model.Role;
import com.medisure.hims.model.User;
import com.medisure.hims.service.AuditService;
import com.medisure.hims.service.PolicyService;
import com.medisure.hims.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
public class ProfileController {

    private final UserService userService;
    private final PolicyService policyService;
    private final AuditService auditService;

    public ProfileController(UserService userService, PolicyService policyService, AuditService auditService) {
        this.userService = userService;
        this.policyService = policyService;
        this.auditService = auditService;
    }

    @GetMapping("/profile")
    public String myProfile(@AuthenticationPrincipal UserPrincipal principal, Model model) {
        User user = userService.findById(principal.getUser().getId());
        model.addAttribute("member", user);
        model.addAttribute("ownProfile", true);
        if (user.getRole() == Role.POLICYHOLDER) {
            model.addAttribute("policies", policyService.findAllFor(user));
            model.addAttribute("accessHistory", auditService.accessHistoryFor(user));
        }
        return "profile/view";
    }

    @GetMapping("/profile/edit")
    public String editForm(@AuthenticationPrincipal UserPrincipal principal, Model model) {
        if (!model.containsAttribute("member")) {
            model.addAttribute("member", userService.findById(principal.getUser().getId()));
        }
        return "profile/edit";
    }

    @PostMapping("/profile")
    public String update(@Valid @ModelAttribute("member") User member, BindingResult result,
                          @AuthenticationPrincipal UserPrincipal principal, Model model) {
        if (result.hasErrors()) {
            return "profile/edit";
        }
        try {
            userService.updateProfile(principal.getUser().getId(), member, principal.getUser());
        } catch (IllegalArgumentException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            return "profile/edit";
        }
        return "redirect:/profile?saved";
    }

    /**
     * Full member record for staff who need it while underwriting, selling or assessing a claim.
     * Customer Relations is deliberately excluded — a support agent does not need a member's
     * medical history to answer a ticket.
     */
    @GetMapping("/members/{id}")
    public String memberProfile(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal,
                                 Model model) {
        User viewer = principal.getUser();
        boolean privileged = viewer.getRole() == Role.ADMIN
                || viewer.getRole() == Role.SALES_AGENT
                || viewer.getRole() == Role.UNDERWRITER
                || viewer.getRole() == Role.CLAIMS_OFFICER;
        if (!privileged && !viewer.getId().equals(id)) {
            throw new AccessDeniedException("You may not view this member's record");
        }

        User member = userService.findById(id);
        auditService.logAccess(member, viewer, AuditService.VIEWED_PROFILE,
                "Member record (personal and medical details)");
        model.addAttribute("member", member);
        model.addAttribute("ownProfile", viewer.getId().equals(id));
        model.addAttribute("policies", policyService.findAllFor(member));
        return "profile/view";
    }
}
