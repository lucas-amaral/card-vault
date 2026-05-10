package br.com.amaral.cardvault.services;

import br.com.amaral.cardvault.utils.CardEncryptionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CardEncryptionUtil}.
 */
class CardEncryptionUtilTest {

    private CardEncryptionUtil encryptionUtil;

    @BeforeEach
    void setUp() {
        encryptionUtil = new CardEncryptionUtil("0123456789abcdef0123456789abcdef");
    }

    @Test
    @DisplayName("encrypt then decrypt returns original card number")
    void encryptDecrypt_roundTrip_returnsOriginal() {
        String cardNumber = "4111111111111111";
        String encrypted = encryptionUtil.encrypt(cardNumber);
        String decrypted = encryptionUtil.decrypt(encrypted);
        assertThat(decrypted).isEqualTo(cardNumber);
    }

    @Test
    @DisplayName("encrypt produces different ciphertext each call due to random IV")
    void encrypt_differentIvEachCall_producesDifferentCiphertext() {
        String cardNumber = "4111111111111111";
        String enc1 = encryptionUtil.encrypt(cardNumber);
        String enc2 = encryptionUtil.encrypt(cardNumber);
        assertThat(enc1).isNotEqualTo(enc2);
    }

    @Test
    @DisplayName("hash is deterministic for the same input")
    void hash_sameInput_returnsSameHash() {
        String hash1 = encryptionUtil.hash("4111111111111111");
        String hash2 = encryptionUtil.hash("4111111111111111");
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    @DisplayName("hash differs for different card numbers")
    void hash_differentInputs_returnsDifferentHashes() {
        String hash1 = encryptionUtil.hash("4111111111111111");
        String hash2 = encryptionUtil.hash("5500005555555559");
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    @DisplayName("hash output is 64 hex characters (SHA-256)")
    void hash_output_is64CharHex() {
        String hash = encryptionUtil.hash("4111111111111111");
        assertThat(hash).hasSize(64).matches("[a-f0-9]+");
    }
}
