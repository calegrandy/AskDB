package io.github.calegrandy.askdb.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts stored database passwords with AES-256-GCM. GCM also authenticates: a value encrypted with another
 * key, or changed in the database, fails to decrypt instead of returning garbage.
 * Stored format: "v1:" + base64(12-byte random IV + ciphertext + 16-byte tag). The version prefix leaves room
 * to rotate keys or change the algorithm later without guessing which values use which.
 */
@Component
public class PasswordCipher {

    private static final String VERSION = "v1:";
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public PasswordCipher(@Value("${askdb.encryption.key}") String base64Key) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException("askdb.encryption.key must be 32 bytes (256 bits), base64-encoded; "
                    + "generate one with: openssl rand -base64 32");
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);  // never reuse an IV with the same key: GCM's security depends on it
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return VERSION + Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Couldn't encrypt the password", e);
        }
    }

    public String decrypt(String stored) {
        if (!stored.startsWith(VERSION)) {
            throw new IllegalStateException("Stored password isn't encrypted in a known format");
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(stored.substring(VERSION.length()));
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES));
            return new String(cipher.doFinal(bytes, IV_BYTES, bytes.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // Don't include the stored value in the message: it would end up in logs.
            throw new IllegalStateException("Couldn't decrypt the stored password: wrong key or corrupted value", e);
        }
    }
}
