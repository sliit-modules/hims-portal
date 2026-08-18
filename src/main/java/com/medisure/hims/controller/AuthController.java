package com.medisure.hims.controller;

import com.medisure.hims.model.User;
import com.medisure.hims.service.UserService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
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
                            Model model) {
        model.addAttribute("mode", "signup");

        if (password == null || password.length() < 6) {
            bindingResult.reject("password.tooShort", "Password must be at least 6 characters");
        } else if (!password.equals(confirmPassword)) {
            bindingResult.reject("password.mismatch", "Passwords do not match");
        }
        if (bindingResult.hasErrors()) {
            return "auth/auth";
        }
        try {
            userService.registerPolicyholder(user, password);
        } catch (IllegalArgumentException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            return "auth/auth";
        }
        return "redirect:/login?registered";
    }
}
