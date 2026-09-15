package com.medisure.hims.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final PrivacyConsentInterceptor privacyConsentInterceptor;

    public WebConfig(PrivacyConsentInterceptor privacyConsentInterceptor) {
        this.privacyConsentInterceptor = privacyConsentInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // The notice itself, sign-in/out, errors and static files must stay reachable without consent.
        registry.addInterceptor(privacyConsentInterceptor)
                .excludePathPatterns("/privacy", "/privacy/**", "/login", "/logout", "/register",
                        "/error", "/css/**", "/js/**");
    }
}
