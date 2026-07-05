package io.github.pratikpanchal22.authserver.security;

import io.github.pratikpanchal22.authserver.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Intercepts fully-authenticated users who have mfa_required=true but have not yet enrolled.
 * Redirects them to /mfa/enroll until enrollment is complete.
 *
 * Registered as a Spring Security filter via SecurityConfig.
 * FilterRegistrationBean in SecurityConfig disables servlet-container auto-registration
 * to prevent the filter running twice.
 */
@Component
public class MfaEnrollmentFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    public MfaEnrollmentFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/mfa/")
                || path.startsWith("/css/")
                || path.startsWith("/js/")
                || path.startsWith("/images/")
                || path.startsWith("/webjars/")
                || path.startsWith("/oauth2/")
                || path.startsWith("/hrd/")
                || path.startsWith("/actuator/")
                || path.equals("/login")
                || path.equals("/logout")
                || path.equals("/error")
                || path.equals("/access-denied");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            chain.doFilter(request, response);
            return;
        }

        // Skip users who are mid-MFA-challenge (PRE_MFA authority)
        boolean preAuth = auth.getAuthorities().stream()
                .anyMatch(a -> "PRE_MFA".equals(a.getAuthority()));
        if (preAuth) {
            chain.doFilter(request, response);
            return;
        }

        var userOpt = userRepository.findByEmail(auth.getName());
        if (userOpt.isPresent()) {
            var user = userOpt.get();
            if (user.isMfaRequired() && !user.isMfaEnabled()) {
                response.sendRedirect(request.getContextPath() + "/mfa/enroll");
                return;
            }
        }

        chain.doFilter(request, response);
    }
}
