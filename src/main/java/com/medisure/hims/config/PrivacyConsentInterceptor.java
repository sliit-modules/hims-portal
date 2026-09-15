package com.medisure.hims.config;

import com.medisure.hims.model.Role;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Sends a signed-in policyholder who has not accepted the current privacy notice to /privacy
 * before they can use any other page. Covers members who registered before consent was captured,
 * and everyone again whenever the notice version changes.
 */
@Component
public class PrivacyConsentInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal
                && principal.getUser().getRole() == Role.POLICYHOLDER
                && !principal.getUser().hasCurrentPrivacyConsent()) {
            response.sendRedirect(request.getContextPath() + "/privacy");
            return false;
        }
        return true;
    }
}
