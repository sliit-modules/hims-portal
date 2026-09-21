package com.medisure.hims.config;

import com.medisure.hims.service.LoginAttemptService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Counts wrong passwords and tells the sign-in page why a sign-in failed. */
@Component
public class LoginFailureHandler implements AuthenticationFailureHandler {

    private final LoginAttemptService loginAttemptService;

    public LoginFailureHandler(LoginAttemptService loginAttemptService) {
        this.loginAttemptService = loginAttemptService;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        String outcome = "error";
        if (exception instanceof LockedException) {
            outcome = "locked";
        } else if (exception instanceof DisabledException) {
            outcome = "disabled";
        } else if (exception instanceof BadCredentialsException
                && loginAttemptService.recordFailure(request.getParameter("identifier"))) {
            outcome = "locked";
        }
        response.sendRedirect(request.getContextPath() + "/login?" + outcome);
    }
}
