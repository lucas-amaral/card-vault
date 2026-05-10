package br.com.amaral.cardvault.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts audit payloads before they are persisted.
 */
@Component
public class PayloadEncryptionUtil {

    private static final String AES_ALGORITHM = "AES/GCM/NoPadding";
    private static final String ENCRYPTED_PREFIX = "enc:v1:";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;

    private final SecretKey aesKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public PayloadEncryptionUtil(@Value("${audit.encryption.key:${card.encryption.key}}") String encryptionKey) {
        byte[] rawSecret = encryptionKey.getBytes(StandardCharsets.UTF_8);
        this.aesKey = new SecretKeySpec(deriveKey(rawSecret), "AES");
    }

    public String encrypt(final String value) {
        if (value == null || value.startsWith(ENCRYPTED_PREFIX)) {
            return value;
        }

        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] cipherText = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] result = new byte[GCM_IV_LENGTH + cipherText.length];
            System.arraycopy(iv, 0, result, 0, GCM_IV_LENGTH);
            System.arraycopy(cipherText, 0, result, GCM_IV_LENGTH, cipherText.length);

            return ENCRYPTED_PREFIX + Base64.getEncoder().encodeToString(result);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt audit payload", e);
        }
    }

    public String decrypt(final String encryptedValue) {
        if (encryptedValue == null || !encryptedValue.startsWith(ENCRYPTED_PREFIX)) {
            return encryptedValue;
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(encryptedValue.substring(ENCRYPTED_PREFIX.length()));
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] cipherText = new byte[decoded.length - GCM_IV_LENGTH];

            System.arraycopy(decoded, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(decoded, GCM_IV_LENGTH, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt audit payload", e);
        }
    }

    private static byte[] deriveKey(final byte[] secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(secret);
            digest.update("audit-payload-encryption".getBytes(StandardCharsets.UTF_8));
            return digest.digest();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive audit encryption key", e);
        }
    }
}
