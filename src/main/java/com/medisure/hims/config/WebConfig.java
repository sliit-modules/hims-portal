package com.medisure.hims.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** Pages that must stay reachable before either check is satisfied. */
    private static final String[] ALWAYS_REACHABLE = {
            "/login", "/logout", "/register", "/error", "/css/**", "/js/**"};

    private final PasswordChangeInterceptor passwordChangeInterceptor;
    private final PrivacyConsentInterceptor privacyConsentInterceptor;

    public WebConfig(PasswordChangeInterceptor passwordChangeInterceptor,
                     PrivacyConsentInterceptor privacyConsentInterceptor) {
        this.passwordChangeInterceptor = passwordChangeInterceptor;
        this.privacyConsentInterceptor = privacyConsentInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Each check must let the other's page through, or a member who has to do both would be
        // bounced between the two pages forever.
        registry.addInterceptor(passwordChangeInterceptor)
                .excludePathPatterns(ALWAYS_REACHABLE)
                .excludePathPatterns("/account/password", "/privacy", "/privacy/**");
        registry.addInterceptor(privacyConsentInterceptor)
                .excludePathPatterns(ALWAYS_REACHABLE)
                .excludePathPatterns("/privacy", "/privacy/**", "/account/password");
    }
}
