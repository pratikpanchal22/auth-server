package io.github.pratikpanchal22.authserver.service;

import io.github.pratikpanchal22.authserver.domain.AuthType;
import io.github.pratikpanchal22.authserver.domain.User;
import io.github.pratikpanchal22.authserver.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class JitOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final OAuth2UserService<OidcUserRequest, OidcUser> delegate;
    private final UserRepository userRepository;
    private final LoginTrackingService loginTrackingService;
    private final AuditService auditService;

    @Autowired
    public JitOidcUserService(UserRepository userRepository,
                              LoginTrackingService loginTrackingService,
                              AuditService auditService) {
        this(new OidcUserService(), userRepository, loginTrackingService, auditService);
    }

    JitOidcUserService(OAuth2UserService<OidcUserRequest, OidcUser> delegate,
                       UserRepository userRepository,
                       LoginTrackingService loginTrackingService,
                       AuditService auditService) {
        this.delegate = delegate;
        this.userRepository = userRepository;
        this.loginTrackingService = loginTrackingService;
        this.auditService = auditService;
    }

    @Override
    @Transactional
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = delegate.loadUser(userRequest);
        String email = oidcUser.getEmail();
        if (email != null) {
            if (!userRepository.existsByEmail(email)) {
                provision(email);
            }
            loginTrackingService.recordSuccess(email);
            auditService.log("FEDERATED_LOGIN", email, currentRequest());
        }
        return withDbRoles(oidcUser, email);
    }

    private OidcUser withDbRoles(OidcUser oidcUser, String email) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>(oidcUser.getAuthorities());
        if (email != null) {
            userRepository.findByEmail(email).ifPresent(user ->
                user.getRoles().forEach(role ->
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + role))
                )
            );
        }
        return new DefaultOidcUser(authorities, oidcUser.getIdToken(), oidcUser.getUserInfo());
    }

    private static HttpServletRequest currentRequest() {
        try {
            return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        } catch (Exception e) {
            return null;
        }
    }

    // Package-private for unit testing without going through full OIDC flow
    void provision(String email) {
        User user = new User();
        user.setEmail(email);
        user.setAuthType(AuthType.FEDERATED);
        user.setActive(true);
        user.getRoles().add("USER");
        userRepository.save(user);
    }
}
