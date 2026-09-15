package com.medisure.hims.controller;

import com.medisure.hims.config.UserPrincipal;
import com.medisure.hims.model.PrivacyNotice;
import com.medisure.hims.model.Role;
import com.medisure.hims.model.User;
import com.medisure.hims.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

/** The public privacy notice, and where a member records their acceptance of it. */
@Controller
public class PrivacyController {

    private final UserService userService;

    public PrivacyController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/privacy")
    public String notice(@AuthenticationPrincipal UserPrincipal principal, Model model) {
        User user = principal == null ? null : principal.getUser();
        model.addAttribute("noticeVersion", PrivacyNotice.VERSION);
        model.addAttribute("signedIn", user != null);
        model.addAttribute("needsConsent", user != null && user.getRole() == Role.POLICYHOLDER
                && !user.hasCurrentPrivacyConsent());
        model.addAttribute("consentedAt", user != null && user.hasCurrentPrivacyConsent()
                ? user.getPrivacyConsentAt() : null);
        return "privacy";
    }

    @PostMapping("/privacy/accept")
    public String accept(@AuthenticationPrincipal UserPrincipal principal) {
        User updated = userService.recordPrivacyConsent(principal.getUser().getId(), principal.getUser());
        // The signed-in session holds its own copy of the user; keep it in step so the member
        // is not sent back to the notice on the next page.
        principal.getUser().setPrivacyConsentAt(updated.getPrivacyConsentAt());
        principal.getUser().setPrivacyNoticeVersion(updated.getPrivacyNoticeVersion());
        return "redirect:/dashboard";
    }
}
