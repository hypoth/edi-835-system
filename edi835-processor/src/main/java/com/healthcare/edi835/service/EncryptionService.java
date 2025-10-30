package com.healthcare.edi835.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Service;

/**
 * Service for encrypting and decrypting sensitive data such as SFTP passwords.
 *
 * Uses Spring Security Crypto's TextEncryptor with AES encryption.
 *
 * Configuration requires:
 * - encryption.key: Secret key for encryption (minimum 8 characters)
 * - encryption.salt: Salt value for encryption (hexadecimal string)
 */
@Service
@Slf4j
public class EncryptionService {

    private final TextEncryptor encryptor;
    private final boolean encryptionEnabled;

    public EncryptionService(
            @Value("${encryption.key:#{null}}") String key,
            @Value("${encryption.salt:#{null}}") String salt) {

        // Check if encryption is properly configured
        if (key != null && !key.isEmpty() && salt != null && !salt.isEmpty()) {
            this.encryptor = Encryptors.text(key, salt);
            this.encryptionEnabled = true;
            log.info("EncryptionService initialized with encryption enabled");
        } else {
            // Fallback: No encryption (not recommended for production)
            this.encryptor = Encryptors.noOpText();
            this.encryptionEnabled = false;
            log.warn("EncryptionService initialized with NO ENCRYPTION (encryption.key or encryption.salt not configured). " +
                     "This is NOT secure for production use!");
        }
    }

    /**
     * Encrypts plaintext password.
     *
     * @param plaintext The password to encrypt
     * @return Encrypted password, or plaintext if encryption is disabled
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }

        try {
            String encrypted = encryptor.encrypt(plaintext);
            if (!encryptionEnabled) {
                log.warn("Encrypting password without proper encryption configuration");
            }
            return encrypted;
        } catch (Exception e) {
            log.error("Failed to encrypt password", e);
            throw new RuntimeException("Failed to encrypt password: " + e.getMessage(), e);
        }
    }

    /**
     * Decrypts encrypted password.
     *
     * @param ciphertext The encrypted password
     * @return Decrypted password, or ciphertext if encryption is disabled
     */
    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isEmpty()) {
            return ciphertext;
        }

        try {
            return encryptor.decrypt(ciphertext);
        } catch (Exception e) {
            log.error("Failed to decrypt password. This may indicate the encryption key/salt has changed, " +
                     "or the password was not properly encrypted", e);
            throw new RuntimeException("Failed to decrypt password: " + e.getMessage(), e);
        }
    }

    /**
     * Checks if encryption is properly configured and enabled.
     *
     * @return true if encryption is enabled, false otherwise
     */
    public boolean isEncryptionEnabled() {
        return encryptionEnabled;
    }
}
