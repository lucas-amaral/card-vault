package br.com.amaral.cardvault.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Utility class providing AES-256-GCM encryption/decryption and HMAC-SHA256 hashing
 * for card numbers. Ensures no plain-text card data is ever persisted.
 *
 * <p>Key derivation: the raw secret from configuration is passed through SHA-256,
 * producing a well-distributed 256-bit key regardless of the input length or entropy
 * distribution. This avoids zero-padding (weak keys) or silent truncation.
 *
 * <p>Two independent keys are derived via domain-separated HMAC so that the AES key
 * and the HMAC key are never the same bytes:
 * <ul>
 *   <li>{@code HMAC-SHA256(rawSecret, "encryption")} → AES-256 key</li>
 *   <li>{@code HMAC-SHA256(rawSecret, "hashing")}    → HMAC-SHA256 lookup key</li>
 * </ul>
 *
 * <p>Using HMAC-SHA256 instead of plain SHA-256 for card lookup prevents rainbow-table
 * attacks: an attacker who obtains the database cannot verify card numbers without
 * also knowing the secret key.
 */
@Component
public class CardEncryptionUtil {

    private static final String AES_ALGORITHM  = "AES/GCM/NoPadding";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH  = 12;

    private final SecretKey aesKey;
    private final SecretKey hmacKey;

    public CardEncryptionUtil(@Value("${card.encryption.key}") String encryptionKey) {
        byte[] rawSecret = encryptionKey.getBytes(StandardCharsets.UTF_8);
        this.aesKey  = new SecretKeySpec(deriveKey(rawSecret, "encryption"), "AES");
        this.hmacKey = new SecretKeySpec(deriveKey(rawSecret, "hashing"),    HMAC_ALGORITHM);
    }

    /**
     * Derives a 256-bit key from {@code secret} and a {@code domain} label using
     * SHA-256, providing domain separation between the AES and HMAC keys.
     */
    private static byte[] deriveKey(final byte[] secret, final String domain) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(secret);
            digest.update(domain.getBytes(StandardCharsets.UTF_8));
            return digest.digest();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive key for domain: " + domain, e);
        }
    }

    /**
     * Encrypts a card number using AES-256-GCM with a random IV per record.
     *
     * @param cardNumber the plain-text card number
     * @return Base64-encoded string: IV (12 bytes) || ciphertext+tag
     */
    public String encrypt(final String cardNumber) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] cipherText = cipher.doFinal(cardNumber.getBytes(StandardCharsets.UTF_8));

            // Prepend IV so it is available for decryption
            byte[] result = new byte[GCM_IV_LENGTH + cipherText.length];
            System.arraycopy(iv,         0, result, 0,             GCM_IV_LENGTH);
            System.arraycopy(cipherText, 0, result, GCM_IV_LENGTH, cipherText.length);

            return Base64.getEncoder().encodeToString(result);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt card number", e);
        }
    }

    /**
     * Decrypts a previously encrypted card number.
     *
     * @param encryptedCard Base64-encoded string: IV || ciphertext+tag
     * @return plain-text card number
     */
    public String decrypt(final String encryptedCard) {
        try {
            byte[] decoded = Base64.getDecoder().decode(encryptedCard);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] cipherText = new byte[decoded.length - GCM_IV_LENGTH];

            System.arraycopy(decoded, 0,             iv,         0, GCM_IV_LENGTH);
            System.arraycopy(decoded, GCM_IV_LENGTH, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt card number", e);
        }
    }

    /**
     * Computes an HMAC-SHA256 of the card number for constant-time lookups.
     * The HMAC is keyed, so the digest cannot be reproduced without the secret key.
     *
     * @param cardNumber the plain-text card number
     * @return lowercase hex-encoded HMAC-SHA256 (64 characters)
     */
    public String hash(final String cardNumber) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(hmacKey);
            byte[] hmacBytes = mac.doFinal(cardNumber.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hmacBytes.length * 2);
            for (byte b : hmacBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to HMAC-hash card number", e);
        }
    }
}