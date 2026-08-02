package io.github.pratikpanchal22.authserver.service;

import io.github.pratikpanchal22.authserver.domain.AuthType;
import io.github.pratikpanchal22.authserver.domain.User;
import io.github.pratikpanchal22.authserver.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JitOidcUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private LoginTrackingService loginTrackingService;

    @Mock
    private AuditService auditService;

    @Mock
    private OAuth2UserService<OidcUserRequest, OidcUser> delegate;

    @Test
    void provision_newUser_savesWithFederatedAuthType() {
        JitOidcUserService service = serviceWithRealDelegate();
        service.provision("alice@example.com");

        var captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        User saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("alice@example.com");
        assertThat(saved.getAuthType()).isEqualTo(AuthType.FEDERATED);
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getPasswordHash()).isNull();
        assertThat(saved.getRoles()).containsExactly("USER");
    }

    @Test
    void provision_setsActiveTrue() {
        JitOidcUserService service = serviceWithRealDelegate();
        service.provision("bob@example.com");

        var captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().isActive()).isTrue();
    }

    @Test
    void provision_setsRoleUser() {
        JitOidcUserService service = serviceWithRealDelegate();
        service.provision("carol@example.com");

        var captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getRoles()).containsExactly("USER");
    }

    @Test
    void loadUser_mergesDbRolesIntoReturnedPrincipal() {
        OidcUser googleUser = minimalOidcUser("author@example.com");
        when(delegate.loadUser(any())).thenReturn(googleUser);
        when(userRepository.existsByEmail("author@example.com")).thenReturn(true);

        User dbUser = new User();
        dbUser.setEmail("author@example.com");
        dbUser.setAuthType(AuthType.FEDERATED);
        dbUser.setActive(true);
        dbUser.getRoles().addAll(Set.of("USER", "AUTHOR"));
        when(userRepository.findByEmail("author@example.com")).thenReturn(Optional.of(dbUser));

        JitOidcUserService service = new JitOidcUserService(delegate, userRepository, loginTrackingService, auditService);
        OidcUser result = service.loadUser(mock(OidcUserRequest.class));

        assertThat(result.getAuthorities())
                .map(a -> a.getAuthority())
                .contains("ROLE_USER", "ROLE_AUTHOR");
    }

    @Test
    void loadUser_existingUserWithNoExtraRoles_returnsOnlyBaseAuthorities() {
        OidcUser googleUser = minimalOidcUser("plain@example.com");
        when(delegate.loadUser(any())).thenReturn(googleUser);
        when(userRepository.existsByEmail("plain@example.com")).thenReturn(true);

        User dbUser = new User();
        dbUser.setEmail("plain@example.com");
        dbUser.setAuthType(AuthType.FEDERATED);
        dbUser.setActive(true);
        dbUser.getRoles().add("USER");
        when(userRepository.findByEmail("plain@example.com")).thenReturn(Optional.of(dbUser));

        JitOidcUserService service = new JitOidcUserService(delegate, userRepository, loginTrackingService, auditService);
        OidcUser result = service.loadUser(mock(OidcUserRequest.class));

        assertThat(result.getAuthorities())
                .map(a -> a.getAuthority())
                .contains("ROLE_USER")
                .doesNotContain("ROLE_AUTHOR");
    }

    private static OidcUser minimalOidcUser(String email) {
        OidcIdToken idToken = OidcIdToken.withTokenValue("token")
                .subject("sub-123")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("email", email)
                .build();
        return new DefaultOidcUser(
                List.of(new SimpleGrantedAuthority("OIDC_USER")),
                idToken
        );
    }

    private JitOidcUserService serviceWithRealDelegate() {
        return new JitOidcUserService(delegate, userRepository, loginTrackingService, auditService);
    }
}
