package io.github.calegrandy.askdb.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

/**
 * Encrypts a column on save and decrypts it on load, so the rest of the code only ever sees plaintext.
 * A Spring bean: Spring Boot lets Hibernate create converters through Spring, so the key can be injected.
 */
@Component
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private final PasswordCipher cipher;

    public EncryptedStringConverter(PasswordCipher cipher) {
        this.cipher = cipher;
    }

    @Override
    public String convertToDatabaseColumn(String plaintext) {
        return plaintext == null ? null : cipher.encrypt(plaintext);
    }

    @Override
    public String convertToEntityAttribute(String stored) {
        return stored == null ? null : cipher.decrypt(stored);
    }
}
