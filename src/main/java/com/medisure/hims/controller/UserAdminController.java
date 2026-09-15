package com.medisure.hims.controller;

import com.medisure.hims.model.Role;
import com.medisure.hims.model.User;
import com.medisure.hims.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;

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
                "privacyConsentAt", "privacyNoticeVersion");
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
}
