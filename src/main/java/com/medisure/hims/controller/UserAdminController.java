package com.medisure.hims.controller;

import com.medisure.hims.config.UserPrincipal;
import com.medisure.hims.model.Role;
import com.medisure.hims.model.User;
import com.medisure.hims.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/users")
public class UserAdminController {

    private final UserService userService;

    public UserAdminController(UserService userService) {
        this.userService = userService;
    }

    /** The role arrives as its own parameter; the id and other server-owned fields never come from the form. */
    @InitBinder("user")
    void protectServerOwnedFields(WebDataBinder binder) {
        binder.setDisallowedFields("id", "role", "passwordHash", "enabled",
                "privacyConsentAt", "privacyNoticeVersion",
                "failedLoginAttempts", "lockedUntil", "passwordChangeRequired");
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("users", userService.findAll());
        return "users/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        if (!model.containsAttribute("user")) {
            model.addAttribute("user", new User());
        }
        model.addAttribute("roles", Role.values());
        return "users/form";
    }

    @PostMapping
    public String create(@ModelAttribute User user, @RequestParam String password,
                          @RequestParam Role role, Model model) {
        if (password == null || password.length() < UserService.MIN_PASSWORD_LENGTH) {
            model.addAttribute("errorMessage",
                    "Password must be at least " + UserService.MIN_PASSWORD_LENGTH + " characters");
            model.addAttribute("roles", Role.values());
            return "users/form";
        }
        try {
            userService.createStaffUser(user, password, role);
        } catch (IllegalArgumentException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("roles", Role.values());
            return "users/form";
        }
        return "redirect:/users";
    }

    @PostMapping("/{id}/disable")
    public String disable(@PathVariable Long id) {
        userService.setEnabled(id, false);
        return "redirect:/users";
    }

    @PostMapping("/{id}/enable")
    public String enable(@PathVariable Long id) {
        userService.setEnabled(id, true);
        return "redirect:/users";
    }

    /** Issues a temporary password, shown to the administrator once on the next page only. */
    @PostMapping("/{id}/reset-password")
    public String resetPassword(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal,
                                RedirectAttributes redirect) {
        String name = userService.findById(id).getFullName();
        String temporary = userService.resetPassword(id, principal.getUser());
        redirect.addFlashAttribute("resetFor", name);
        redirect.addFlashAttribute("temporaryPassword", temporary);
        return "redirect:/users";
    }

    @PostMapping("/{id}/unlock")
    public String unlock(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal,
                         RedirectAttributes redirect) {
        userService.unlock(id, principal.getUser());
        redirect.addFlashAttribute("unlocked", userService.findById(id).getFullName());
        return "redirect:/users";
    }
}
