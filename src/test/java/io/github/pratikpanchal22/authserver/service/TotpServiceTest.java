package io.github.pratikpanchal22.authserver.service;

import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TotpServiceTest {

    private final TotpService service = new TotpService();

    @Test
    void generateSecret_returnsNonBlankBase32String() {
        String secret = service.generateSecret();
        assertThat(secret).isNotBlank();
        assertThat(secret).matches("[A-Z2-7]+=*"); // Base32 alphabet
    }

    @Test
    void generateSecret_returnsDifferentValueEachTime() {
        assertThat(service.generateSecret()).isNotEqualTo(service.generateSecret());
    }

    @Test
    void generateOtpauthUri_containsRequiredParts() {
        String secret = service.generateSecret();
        String uri = service.generateOtpauthUri(secret, "alice@example.com");
        assertThat(uri).startsWith("otpauth://totp/");
        assertThat(uri).contains("secret=" + secret);
        assertThat(uri).contains("issuer=nthNode");
    }

    @Test
    void isValidCode_currentCode_returnsTrue() throws Exception {
        String secret = service.generateSecret();
        long counter = Math.floorDiv(new SystemTimeProvider().getTime(), 30);
        String currentCode = new DefaultCodeGenerator().generate(secret, counter);
        assertThat(service.isValidCode(secret, currentCode)).isTrue();
    }

    @Test
    void isValidCode_wrongCode_returnsFalse() {
        String secret = service.generateSecret();
        assertThat(service.isValidCode(secret, "000000")).isFalse();
    }

    @Test
    void isValidCode_blankCode_returnsFalse() {
        assertThat(service.isValidCode(service.generateSecret(), "")).isFalse();
    }
}
