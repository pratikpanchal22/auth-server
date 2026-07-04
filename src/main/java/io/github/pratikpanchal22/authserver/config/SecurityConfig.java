package io.github.pratikpanchal22.authserver.config;

import io.github.pratikpanchal22.authserver.service.JitOidcUserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JitOidcUserService jitOidcUserService;
    private final MfaAuthenticationSuccessHandler mfaSuccessHandler;

    public SecurityConfig(JitOidcUserService jitOidcUserService,
                          MfaAuthenticationSuccessHandler mfaSuccessHandler) {
        this.jitOidcUserService = jitOidcUserService;
        this.mfaSuccessHandler = mfaSuccessHandler;
    }

    @Bean
    @Order(2)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/login", "/error").permitAll()
                .requestMatchers("/hrd/lookup").permitAll()
                .requestMatchers("/css/**", "/js/**", "/images/**", "/webjars/**").permitAll()
                .requestMatchers("/mfa/challenge").hasAuthority("PRE_MFA")
                .requestMatchers("/mfa/enroll", "/mfa/enroll/confirm", "/mfa/recovery-codes").authenticated()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .usernameParameter("email")
                .passwordParameter("password")
                .successHandler(mfaSuccessHandler)
                .failureUrl("/login?error")
                .permitAll()
            )
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/login")
                .defaultSuccessUrl("/", true)
                .userInfoEndpoint(info -> info.oidcUserService(jitOidcUserService))
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/login?logout")
                .permitAll()
            );
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
