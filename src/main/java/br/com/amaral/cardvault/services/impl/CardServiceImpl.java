package br.com.amaral.cardvault.services.impl;

import br.com.amaral.cardvault.entities.Card;
import br.com.amaral.cardvault.entities.User;
import br.com.amaral.cardvault.entities.dto.response.CardResponse;
import br.com.amaral.cardvault.exceptions.ResourceNotFoundException;
import br.com.amaral.cardvault.repositories.CardRepository;
import br.com.amaral.cardvault.repositories.UserRepository;
import br.com.amaral.cardvault.services.CardService;
import br.com.amaral.cardvault.utils.CardEncryptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Default implementation of {@link CardService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CardServiceImpl implements CardService {

    private final CardRepository cardRepository;
    private final UserRepository userRepository;
    private final CardEncryptionUtil encryptionUtil;

    @Override
    @Transactional
    public RegisterResult registerCard(final String cardNumber, final String username) {
        final String hash = encryptionUtil.hash(cardNumber);

        final Optional<Card> existing = cardRepository.findByCardHash(hash);
        if (existing.isPresent()) {
            log.info("Card already registered, returning existing record: user={}", username);
            return new RegisterResult(toResponse(existing.get()), true);
        }

        final User user = resolveUser(username);
        final Card saved = cardRepository.save(Card.builder()
                .externalId(UUID.randomUUID().toString())
                .cardNumberEnc(encryptionUtil.encrypt(cardNumber))
                .cardHash(hash)
                .createdBy(user)
                .build());

        log.info("Card registered: externalId={}, user={}", saved.getExternalId(), username);
        return new RegisterResult(toResponse(saved), false);
    }

    @Override
    @Transactional(readOnly = true)
    public CardResponse findCard(final String cardNumber) {
        final String hash = encryptionUtil.hash(cardNumber);
        return cardRepository.findByCardHash(hash)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Card not found"));
    }

    private User resolveUser(final String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + username));
    }

    private CardResponse toResponse(final Card card) {
        return new CardResponse(card.getExternalId(), card.getBatchName(), card.getCreatedAt());
    }
}
