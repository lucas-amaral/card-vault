package br.com.amaral.cardvault.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link PayloadEncryptionUtil}.
 */
class PayloadEncryptionUtilTest {

    private PayloadEncryptionUtil payloadEncryptionUtil;

    @BeforeEach
    void setUp() {
        payloadEncryptionUtil = new PayloadEncryptionUtil("0123456789abcdef0123456789abcdef");
    }

    @Test
    @DisplayName("encrypt/decrypt — restores original payload")
    void encryptDecrypt_restoresOriginalPayload() {
        String payload = "{\"username\":\"admin\",\"password\":\"Admin@123\"}";

        String encrypted = payloadEncryptionUtil.encrypt(payload);
        String decrypted = payloadEncryptionUtil.decrypt(encrypted);

        assertThat(encrypted).startsWith("enc:v1:");
        assertThat(encrypted).doesNotContain("Admin@123");
        assertThat(decrypted).isEqualTo(payload);
    }

    @Test
    @DisplayName("encrypt — uses random IV, producing different ciphertext for same payload")
    void encrypt_samePayload_producesDifferentCiphertext() {
        String payload = "{\"status\":\"ok\"}";

        String encrypted1 = payloadEncryptionUtil.encrypt(payload);
        String encrypted2 = payloadEncryptionUtil.encrypt(payload);

        assertThat(encrypted1).isNotEqualTo(encrypted2);
        assertThat(payloadEncryptionUtil.decrypt(encrypted1)).isEqualTo(payload);
        assertThat(payloadEncryptionUtil.decrypt(encrypted2)).isEqualTo(payload);
    }

    @Test
    @DisplayName("encrypt/decrypt — null values remain null")
    void encryptDecrypt_null_remainsNull() {
        assertThat(payloadEncryptionUtil.encrypt(null)).isNull();
        assertThat(payloadEncryptionUtil.decrypt(null)).isNull();
    }

    @Test
    @DisplayName("encrypt — already encrypted value is returned unchanged")
    void encrypt_alreadyEncrypted_returnsUnchanged() {
        String encrypted = payloadEncryptionUtil.encrypt("{\"data\":\"value\"}");

        assertThat(payloadEncryptionUtil.encrypt(encrypted)).isEqualTo(encrypted);
    }

    @Test
    @DisplayName("decrypt — plaintext value is returned unchanged")
    void decrypt_plaintext_returnsUnchanged() {
        assertThat(payloadEncryptionUtil.decrypt("{\"status\":\"ok\"}"))
                .isEqualTo("{\"status\":\"ok\"}");
    }

    @Test
    @DisplayName("decrypt — wrong key throws exception")
    void decrypt_withWrongKey_throwsException() {
        String encrypted = payloadEncryptionUtil.encrypt("{\"secret\":\"value\"}");
        PayloadEncryptionUtil otherKey = new PayloadEncryptionUtil("abcdef0123456789abcdef0123456789");

        assertThatThrownBy(() -> otherKey.decrypt(encrypted))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to decrypt audit payload");
    }
}
