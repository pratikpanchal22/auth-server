package io.github.pratikpanchal22.authserver.security;

import io.github.pratikpanchal22.authserver.domain.User;
import io.github.pratikpanchal22.authserver.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

public class ClientAccessFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    public ClientAccessFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String clientId = request.getParameter("client_id");
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (clientId == null || auth == null
                || !auth.isAuthenticated()
                || auth instanceof AnonymousAuthenticationToken) {
            chain.doFilter(request, response);
            return;
        }

        // PRE_MFA tokens haven't completed the full login flow — let AS redirect them
        boolean preAuth = auth.getAuthorities().stream()
                .anyMatch(a -> "PRE_MFA".equals(a.getAuthority()));
        if (preAuth) {
            chain.doFilter(request, response);
            return;
        }

        Optional<User> userOpt = userRepository.findByEmail(auth.getName());
        if (userOpt.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }

        Set<String> allowed = userOpt.get().getAllowedClients();
        // empty set = unrestricted; non-empty = enforce allowlist
        if (!allowed.isEmpty() && !allowed.contains(clientId)) {
            response.sendRedirect(request.getContextPath() + "/access-denied");
            return;
        }

        chain.doFilter(request, response);
    }
}
