package com.medisure.hims.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * After an administrator resets a password, the user signs in with a temporary password and must
 * choose their own before they can use any other page.
 */
@Component
public class PasswordChangeInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal
                && principal.getUser().mustChangePassword()) {
            response.sendRedirect(request.getContextPath() + "/account/password");
            return false;
        }
        return true;
    }
}
