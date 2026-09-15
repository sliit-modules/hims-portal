package com.medisure.hims.controller;

import com.medisure.hims.model.User;
import com.medisure.hims.service.UserService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;

/**
 * Sign-in and registration share one screen; {@code mode} decides which half of the sliding
 * panel is showing when the page loads, so a failed registration comes back on the sign-up side.
 */
@Controller
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    /**
     * The sign-up form binds straight onto a User, so fields the server owns must never come from
     * the request — a submitted "id" would otherwise turn registration into an overwrite of an
     * existing account (including the administrator's).
     */
    @InitBinder("user")
    void protectServerOwnedFields(WebDataBinder binder) {
        binder.setDisallowedFields("id", "role", "passwordHash", "enabled",
                "privacyConsentAt", "privacyNoticeVersion");
    }

    @GetMapping("/login")
    public String loginPage(Model model) {
        model.addAttribute("mode", "signin");
        if (!model.containsAttribute("user")) {
            model.addAttribute("user", new User());
        }
        return "auth/auth";
    }

    @GetMapping("/register")
    public String registerForm(Model model) {
        model.addAttribute("mode", "signup");
        if (!model.containsAttribute("user")) {
            model.addAttribute("user", new User());
        }
        return "auth/auth";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("user") User user, BindingResult bindingResult,
                            @RequestParam String password, @RequestParam String confirmPassword,
                            @RequestParam(defaultValue = "false") boolean acceptPrivacy,
                            Model model) {
        model.addAttribute("mode", "signup");
        model.addAttribute("acceptPrivacy", acceptPrivacy);

        if (password == null || password.length() < 6) {
            bindingResult.reject("password.tooShort", "Password must be at least 6 characters");
        } else if (!password.equals(confirmPassword)) {
            bindingResult.reject("password.mismatch", "Passwords do not match");
        }
        if (!acceptPrivacy) {
            bindingResult.reject("privacy.required", "Please read and accept the Privacy Notice to create an account");
        }
        if (bindingResult.hasErrors()) {
            return "auth/auth";
        }
        try {
            userService.registerPolicyholder(user, password, acceptPrivacy);
        } catch (IllegalArgumentException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            return "auth/auth";
        }
        return "redirect:/login?registered";
    }
}
