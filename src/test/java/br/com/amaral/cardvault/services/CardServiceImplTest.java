package br.com.amaral.cardvault.services;

import br.com.amaral.cardvault.entities.dto.response.CardResponse;
import br.com.amaral.cardvault.entities.Card;
import br.com.amaral.cardvault.entities.User;
import br.com.amaral.cardvault.exceptions.ResourceNotFoundException;
import br.com.amaral.cardvault.repositories.CardRepository;
import br.com.amaral.cardvault.repositories.UserRepository;
import br.com.amaral.cardvault.services.impl.CardServiceImpl;
import br.com.amaral.cardvault.utils.CardEncryptionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CardServiceImpl}.
 */
@ExtendWith(MockitoExtension.class)
class CardServiceImplTest {

    @Mock private CardRepository     cardRepository;
    @Mock private UserRepository     userRepository;
    @Mock private CardEncryptionUtil encryptionUtil;

    @InjectMocks
    private CardServiceImpl cardService;

    private User testUser;
    private Card testCard;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .username("testuser")
                .password("hashed")
                .role("ROLE_USER")
                .enabled(true)
                .build();

        testCard = Card.builder()
                .id(1L)
                .externalId(UUID.randomUUID().toString())
                .cardNumberEnc("encrypted")
                .cardHash("hash123")
                .createdBy(testUser)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // -------------------------------------------------------------------------
    // registerCard
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("registerCard — new card: saves, returns alreadyExisted=false")
    void registerCard_newCard_savesAndReturnsNotExisted() {
        when(encryptionUtil.hash("4111111111111111")).thenReturn("newhash");
        when(encryptionUtil.encrypt("4111111111111111")).thenReturn("encrypted");
        when(cardRepository.findByCardHash("newhash")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(cardRepository.save(any(Card.class))).thenAnswer(inv -> {
            Card c = inv.getArgument(0);
            c.setCreatedAt(LocalDateTime.now());
            return c;
        });

        CardService.RegisterResult result = cardService.registerCard("4111111111111111", "testuser");

        assertThat(result.alreadyExisted()).isFalse();
        assertThat(result.card().id()).isNotBlank();
        verify(cardRepository).save(any(Card.class));
    }

    @Test
    @DisplayName("registerCard — existing card: returns alreadyExisted=true, skips save")
    void registerCard_existingCard_returnsExistedTrueWithoutSave() {
        when(encryptionUtil.hash("4111111111111111")).thenReturn("hash123");
        when(cardRepository.findByCardHash("hash123")).thenReturn(Optional.of(testCard));

        CardService.RegisterResult result = cardService.registerCard("4111111111111111", "testuser");

        assertThat(result.alreadyExisted()).isTrue();
        assertThat(result.card().id()).isEqualTo(testCard.getExternalId());
        verify(cardRepository, never()).save(any());
        verify(userRepository, never()).findByUsername(any());
    }

    @Test
    @DisplayName("registerCard — unknown user throws IllegalStateException")
    void registerCard_unknownUser_throwsException() {
        when(encryptionUtil.hash("4111111111111111")).thenReturn("newhash");
        when(cardRepository.findByCardHash("newhash")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cardService.registerCard("4111111111111111", "ghost"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ghost");
    }

    @Test
    @DisplayName("registerCard — returned card contains batchName from entity")
    void registerCard_newCard_batchNamePropagated() {
        testCard.setBatchName("LOTE0001");
        when(encryptionUtil.hash("4111111111111111")).thenReturn("hash123");
        when(cardRepository.findByCardHash("hash123")).thenReturn(Optional.of(testCard));

        CardService.RegisterResult result = cardService.registerCard("4111111111111111", "testuser");

        assertThat(result.card().batchName()).isEqualTo("LOTE0001");
    }

    // -------------------------------------------------------------------------
    // findCard
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findCard — existing card returns CardResponse")
    void findCard_exists_returnsResponse() {
        when(encryptionUtil.hash("4111111111111111")).thenReturn("hash123");
        when(cardRepository.findByCardHash("hash123")).thenReturn(Optional.of(testCard));

        CardResponse result = cardService.findCard("4111111111111111");

        assertThat(result.id()).isEqualTo(testCard.getExternalId());
    }

    @Test
    @DisplayName("findCard — card not found throws ResourceNotFoundException")
    void findCard_notFound_throwsResourceNotFoundException() {
        when(encryptionUtil.hash("9999999999999999")).thenReturn("missinghash");
        when(cardRepository.findByCardHash("missinghash")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cardService.findCard("9999999999999999"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Card not found");
    }

    @Test
    @DisplayName("findCard — delegates hash computation to encryptionUtil")
    void findCard_delegatesHashToEncryptionUtil() {
        when(encryptionUtil.hash("4111111111111111")).thenReturn("hash123");
        when(cardRepository.findByCardHash("hash123")).thenReturn(Optional.of(testCard));

        cardService.findCard("4111111111111111");

        verify(encryptionUtil).hash("4111111111111111");
        verify(cardRepository).findByCardHash("hash123");
    }
}
