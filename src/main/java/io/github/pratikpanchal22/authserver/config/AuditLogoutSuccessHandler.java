package io.github.pratikpanchal22.authserver.config;

import io.github.pratikpanchal22.authserver.service.AuditService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class AuditLogoutSuccessHandler extends SimpleUrlLogoutSuccessHandler {

    private final AuditService auditService;

    public AuditLogoutSuccessHandler(AuditService auditService) {
        setDefaultTargetUrl("/login?logout");
        this.auditService = auditService;
    }

    @Override
    public void onLogoutSuccess(HttpServletRequest request,
                                HttpServletResponse response,
                                Authentication authentication) throws IOException, ServletException {
        if (authentication != null) {
            auditService.log("LOGOUT", authentication.getName(), request);
        }
        super.onLogoutSuccess(request, response, authentication);
    }
}
