package io.github.pratikpanchal22.authserver.config;

import io.github.pratikpanchal22.authserver.service.AuditService;
import io.github.pratikpanchal22.authserver.service.LoginTrackingService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class LoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private final LoginTrackingService loginTrackingService;
    private final AuditService auditService;

    public LoginFailureHandler(LoginTrackingService loginTrackingService, AuditService auditService) {
        super("/login?error");
        this.loginTrackingService = loginTrackingService;
        this.auditService = auditService;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        String email = request.getParameter("email");
        if (email != null && !email.isBlank()) {
            String trimmed = email.trim();
            loginTrackingService.recordFailure(trimmed);
            auditService.log("LOGIN_FAILURE", trimmed, request,
                    java.util.Map.of("reason", exception.getMessage() != null ? exception.getMessage() : "bad credentials"));
        }
        super.onAuthenticationFailure(request, response, exception);
    }
}
