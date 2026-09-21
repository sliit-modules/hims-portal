package com.medisure.hims.controller;

import com.medisure.hims.config.UserPrincipal;
import com.medisure.hims.model.User;
import com.medisure.hims.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Lets any signed-in user change their own password (and finish a reset by an administrator). */
@Controller
@RequestMapping("/account/password")
public class AccountController {

    private final UserService userService;

    public AccountController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public String form(@AuthenticationPrincipal UserPrincipal principal, Model model) {
        model.addAttribute("required", principal.getUser().mustChangePassword());
        return "account/password";
    }

    @PostMapping
    public String change(@RequestParam String currentPassword, @RequestParam String newPassword,
                         @RequestParam String confirmPassword,
                         @AuthenticationPrincipal UserPrincipal principal, Model model) {
        try {
            User updated = userService.changePassword(principal.getUser().getId(), currentPassword,
                    newPassword, confirmPassword, principal.getUser());
            // The signed-in session holds its own copy of the user; keep it in step.
            principal.getUser().setPasswordHash(updated.getPasswordHash());
            principal.getUser().setPasswordChangeRequired(false);
        } catch (IllegalArgumentException ex) {
            model.addAttribute("required", principal.getUser().mustChangePassword());
            model.addAttribute("errorMessage", ex.getMessage());
            return "account/password";
        }
        return "redirect:/account/password?changed";
    }
}
