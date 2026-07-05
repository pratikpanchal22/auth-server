package io.github.pratikpanchal22.authserver.config;

import io.github.pratikpanchal22.authserver.security.MfaEnrollmentFilter;
import io.github.pratikpanchal22.authserver.service.JitOidcUserService;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JitOidcUserService jitOidcUserService;
    private final MfaAuthenticationSuccessHandler mfaSuccessHandler;
    private final LoginFailureHandler loginFailureHandler;
    private final AuditLogoutSuccessHandler auditLogoutHandler;
    private final MfaEnrollmentFilter mfaEnrollmentFilter;

    public SecurityConfig(JitOidcUserService jitOidcUserService,
                          MfaAuthenticationSuccessHandler mfaSuccessHandler,
                          LoginFailureHandler loginFailureHandler,
                          AuditLogoutSuccessHandler auditLogoutHandler,
                          MfaEnrollmentFilter mfaEnrollmentFilter) {
        this.jitOidcUserService = jitOidcUserService;
        this.mfaSuccessHandler = mfaSuccessHandler;
        this.loginFailureHandler = loginFailureHandler;
        this.auditLogoutHandler = auditLogoutHandler;
        this.mfaEnrollmentFilter = mfaEnrollmentFilter;
    }

    @Bean
    @Order(2)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/login", "/error", "/access-denied").permitAll()
                .requestMatchers("/hrd/lookup").permitAll()
                .requestMatchers("/css/**", "/js/**", "/images/**", "/webjars/**").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/mfa/challenge").hasAuthority("PRE_MFA")
                .requestMatchers("/mfa/enroll", "/mfa/enroll/confirm", "/mfa/recovery-codes").authenticated()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .usernameParameter("email")
                .passwordParameter("password")
                .successHandler(mfaSuccessHandler)
                .failureHandler(loginFailureHandler)
                .permitAll()
            )
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/login")
                .defaultSuccessUrl("/", true)
                .userInfoEndpoint(info -> info.oidcUserService(jitOidcUserService))
            )
            .logout(logout -> logout
                .logoutSuccessHandler(auditLogoutHandler)
                .permitAll()
            );

        http.addFilterAfter(mfaEnrollmentFilter, AnonymousAuthenticationFilter.class);

        return http.build();
    }

    /** Prevent Spring Boot from auto-registering MfaEnrollmentFilter as a plain servlet filter. */
    @Bean
    public FilterRegistrationBean<MfaEnrollmentFilter> mfaEnrollmentFilterRegistration(
            MfaEnrollmentFilter filter) {
        FilterRegistrationBean<MfaEnrollmentFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setEnabled(false);
        return reg;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
