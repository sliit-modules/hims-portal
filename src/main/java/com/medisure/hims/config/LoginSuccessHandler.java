package com.medisure.hims.config;

import com.medisure.hims.service.LoginAttemptService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Clears the wrong-password count, then sends the user to their dashboard — or straight to the
 * change-password page when an administrator has reset their password.
 */
@Component
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final LoginAttemptService loginAttemptService;

    public LoginSuccessHandler(LoginAttemptService loginAttemptService) {
        this.loginAttemptService = loginAttemptService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        String target = "/dashboard";
        if (authentication.getPrincipal() instanceof UserPrincipal principal) {
            loginAttemptService.recordSuccess(principal.getUser());
            if (principal.getUser().mustChangePassword()) {
                target = "/account/password";
            }
        }
        response.sendRedirect(request.getContextPath() + target);
    }
}
