package io.github.pratikpanchal22.authserver.service;

import io.github.pratikpanchal22.authserver.domain.MfaRecoveryCode;
import io.github.pratikpanchal22.authserver.domain.User;
import io.github.pratikpanchal22.authserver.repository.MfaRecoveryCodeRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class RecoveryCodeService {

    // Excludes visually ambiguous chars (0/O, 1/I/L)
    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;
    private static final int CODE_COUNT = 10;

    private final MfaRecoveryCodeRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom random = new SecureRandom();

    public RecoveryCodeService(MfaRecoveryCodeRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public List<String> generateAndStore(User user) {
        List<String> plain = new ArrayList<>(CODE_COUNT);
        for (int i = 0; i < CODE_COUNT; i++) {
            String code = generatePlainCode();
            plain.add(code);
            MfaRecoveryCode entity = new MfaRecoveryCode();
            entity.setUser(user);
            entity.setCodeHash(passwordEncoder.encode(code));
            repository.save(entity);
        }
        return plain;
    }

    @Transactional
    public boolean consumeRecoveryCode(User user, String rawCode) {
        String normalised = rawCode.trim().toUpperCase();
        for (MfaRecoveryCode rc : repository.findByUserIdAndUsedFalse(user.getId())) {
            if (passwordEncoder.matches(normalised, rc.getCodeHash())) {
                rc.setUsed(true);
                rc.setUsedAt(Instant.now());
                repository.save(rc);
                return true;
            }
        }
        return false;
    }

    private String generatePlainCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
