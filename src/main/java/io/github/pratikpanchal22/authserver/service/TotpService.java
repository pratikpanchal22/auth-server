package io.github.pratikpanchal22.authserver.service;

import dev.samstevens.totp.code.*;

import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import org.springframework.stereotype.Service;

@Service
public class TotpService {

    private static final String ISSUER = "nthNode";

    public String generateSecret() {
        return new DefaultSecretGenerator(32).generate();
    }

    public String generateOtpauthUri(String secret, String email) {
        return new QrData.Builder()
                .label(email)
                .secret(secret)
                .issuer(ISSUER)
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build()
                .getUri();
    }

    public boolean isValidCode(String secret, String code) {
        try {
            DefaultCodeVerifier verifier = new DefaultCodeVerifier(
                    new DefaultCodeGenerator(), new SystemTimeProvider());
            verifier.setAllowedTimePeriodDiscrepancy(1);
            return verifier.isValidCode(secret, code);
        } catch (Exception e) {
            return false;
        }
    }
}
