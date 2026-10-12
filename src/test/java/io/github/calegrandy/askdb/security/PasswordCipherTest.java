package io.github.calegrandy.askdb.security;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordCipherTest {

    private static final String KEY = key((byte) 1);

    private final PasswordCipher cipher = new PasswordCipher(KEY);

    @Test
    void decryptsWhatItEncrypts() {
        String encrypted = cipher.encrypt("secret");

        assertThat(encrypted).startsWith("v1:").doesNotContain("secret");
        assertThat(cipher.decrypt(encrypted)).isEqualTo("secret");
    }

    @Test
    void encryptsTheSameValueDifferentlyEachTime() {
        // A random IV per value: equal passwords must not be recognisable as equal in the database.
        assertThat(cipher.encrypt("secret")).isNotEqualTo(cipher.encrypt("secret"));
    }

    @Test
    void failsWithTheWrongKey() {
        String encrypted = cipher.encrypt("secret");

        assertThatThrownBy(() -> new PasswordCipher(key((byte) 2)).decrypt(encrypted))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failsWhenTheStoredValueWasTamperedWith() {
        byte[] bytes = Base64.getDecoder().decode(cipher.encrypt("secret").substring("v1:".length()));
        bytes[bytes.length - 1] ^= 1;  // flip one bit of the authentication tag
        String tampered = "v1:" + Base64.getEncoder().encodeToString(bytes);

        assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failsOnAValueThatWasNeverEncrypted() {
        assertThatThrownBy(() -> cipher.decrypt("secret")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsAKeyThatIsNot256Bits() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> new PasswordCipher(shortKey)).isInstanceOf(IllegalArgumentException.class);
    }

    private static String key(byte fill) {
        byte[] bytes = new byte[32];
        Arrays.fill(bytes, fill);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
