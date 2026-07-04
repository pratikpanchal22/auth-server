package io.github.pratikpanchal22.authserver.config;

import io.github.pratikpanchal22.authserver.repository.UserRepository;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

@Component
public class MfaAuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    public static final String PENDING_MFA_AUTH = "PENDING_MFA_AUTHENTICATION";

    private final UserRepository userRepository;
    private final HttpSessionSecurityContextRepository contextRepository =
            new HttpSessionSecurityContextRepository();

    public MfaAuthenticationSuccessHandler(UserRepository userRepository) {
        super("/");
        this.userRepository = userRepository;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        UserDetails principal = (UserDetails) authentication.getPrincipal();
        boolean mfaRequired = userRepository.findByEmail(principal.getUsername())
                .map(u -> u.isMfaEnabled())
                .orElse(false);

        if (mfaRequired) {
            request.getSession(true).setAttribute(PENDING_MFA_AUTH, authentication);

            var preMfaToken = new UsernamePasswordAuthenticationToken(
                    principal, null,
                    List.of(new SimpleGrantedAuthority("PRE_MFA")));

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(preMfaToken);
            SecurityContextHolder.setContext(context);
            contextRepository.saveContext(context, request, response);

            response.sendRedirect(request.getContextPath() + "/mfa/challenge");
        } else {
            super.onAuthenticationSuccess(request, response, authentication);
        }
    }
}
