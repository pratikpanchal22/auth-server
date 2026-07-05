package io.github.pratikpanchal22.authserver.service;

import io.github.pratikpanchal22.authserver.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class LoginTrackingService {

    private final UserRepository userRepository;

    public LoginTrackingService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public void recordSuccess(String email) {
        userRepository.findByEmail(email).ifPresent(u -> {
            u.setLastLoginAt(Instant.now());
            u.setFailedAttempts(0);
            userRepository.save(u);
        });
    }

    @Transactional
    public void recordFailure(String email) {
        userRepository.findByEmail(email).ifPresent(u -> {
            u.setFailedAttempts(u.getFailedAttempts() + 1);
            userRepository.save(u);
        });
    }

    /** Clears the failed-attempt counter when the password check passes but MFA is still pending. */
    @Transactional
    public void resetFailedAttempts(String email) {
        userRepository.findByEmail(email).ifPresent(u -> {
            u.setFailedAttempts(0);
            userRepository.save(u);
        });
    }
}
