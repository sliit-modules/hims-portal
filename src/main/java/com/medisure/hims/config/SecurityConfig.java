package com.medisure.hims.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, LoginSuccessHandler loginSuccessHandler,
                                           LoginFailureHandler loginFailureHandler) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/login", "/register", "/privacy", "/css/**", "/js/**", "/error").permitAll()

                .requestMatchers(HttpMethod.POST, "/plans/**").hasRole("ADMIN")
                .requestMatchers("/plans/new", "/plans/*/edit").hasRole("ADMIN")
                .requestMatchers("/plans/**").hasAnyRole("ADMIN", "SALES_AGENT", "UNDERWRITER", "POLICYHOLDER")

                .requestMatchers("/underwriting/*/decide").hasAnyRole("UNDERWRITER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/underwriting/*/withdraw").hasAnyRole("POLICYHOLDER", "ADMIN")
                .requestMatchers("/underwriting/new").hasAnyRole("POLICYHOLDER", "SALES_AGENT", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/underwriting").hasAnyRole("POLICYHOLDER", "SALES_AGENT", "ADMIN")
                .requestMatchers("/underwriting/**").hasAnyRole("UNDERWRITER", "ADMIN", "SALES_AGENT", "POLICYHOLDER")

                .requestMatchers(HttpMethod.GET, "/policies/**")
                    .hasAnyRole("SALES_AGENT", "ADMIN", "POLICYHOLDER", "CLAIMS_OFFICER", "CRE")
                .requestMatchers(HttpMethod.POST, "/policies/**").hasAnyRole("SALES_AGENT", "ADMIN")

                .requestMatchers(HttpMethod.GET, "/claims/**")
                    .hasAnyRole("CLAIMS_OFFICER", "ADMIN", "POLICYHOLDER", "SALES_AGENT", "CRE")
                .requestMatchers(HttpMethod.POST, "/claims/new", "/claims").hasAnyRole("POLICYHOLDER", "SALES_AGENT", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/claims/*/status", "/claims/*/void").hasAnyRole("CLAIMS_OFFICER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/claims/*/withdraw").hasAnyRole("POLICYHOLDER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/claims/*/documents/*/delete").hasAnyRole("POLICYHOLDER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/claims/*/documents")
                    .hasAnyRole("POLICYHOLDER", "SALES_AGENT", "CLAIMS_OFFICER", "ADMIN")

                // PayHere's server calls this; it is trusted only after its md5sig signature is checked.
                .requestMatchers(HttpMethod.POST, "/payments/payhere/notify").permitAll()
                .requestMatchers(HttpMethod.GET, "/payments/**").hasAnyRole("POLICYHOLDER", "ADMIN", "CLAIMS_OFFICER", "SALES_AGENT")
                .requestMatchers(HttpMethod.POST, "/payments/*/refund-decision", "/payments/*/void")
                    .hasAnyRole("CLAIMS_OFFICER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/payments/*/refund-request").hasRole("POLICYHOLDER")
                .requestMatchers(HttpMethod.POST, "/payments/**").hasAnyRole("POLICYHOLDER", "ADMIN")

                .requestMatchers("/tickets/**")
                    .hasAnyRole("CRE", "ADMIN", "POLICYHOLDER", "SALES_AGENT", "CLAIMS_OFFICER", "UNDERWRITER")

                .requestMatchers("/members/**")
                    .hasAnyRole("ADMIN", "SALES_AGENT", "UNDERWRITER", "CLAIMS_OFFICER", "POLICYHOLDER")

                .requestMatchers("/audit/**").hasRole("ADMIN")
                .requestMatchers("/reports/**", "/reports").hasRole("ADMIN")
                .requestMatchers("/users/**").hasRole("ADMIN")

                .anyRequest().authenticated()
            )
            // PayHere cannot send our CSRF token; the notification is verified by its signature instead.
            .csrf(csrf -> csrf.ignoringRequestMatchers("/payments/payhere/notify"))
            .formLogin(form -> form
                .loginPage("/login")
                .usernameParameter("identifier")
                .successHandler(loginSuccessHandler)
                .failureHandler(loginFailureHandler)
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .permitAll()
            );

        return http.build();
    }
}
