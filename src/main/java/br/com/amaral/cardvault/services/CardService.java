package br.com.amaral.cardvault.services;

import br.com.amaral.cardvault.entities.dto.response.CardResponse;

/**
 * Business operations for individual card management.
 */
public interface CardService {

    /**
     * Registers a single card number if not already present.
     * Returns both the card data and whether it was newly created,
     * allowing the caller to choose the appropriate HTTP status.
     *
     * @param cardNumber plain-text card number
     * @param username   authenticated user performing the operation
     * @return {@link RegisterResult} containing the card and a flag indicating if it already existed
     */
    RegisterResult registerCard(String cardNumber, String username);

    /**
     * Looks up a card by its number. Throws {@link br.com.amaral.cardvault.exceptions.ResourceNotFoundException}
     * if not found, so the controller never needs to handle the empty case.
     *
     * @param cardNumber plain-text card number to search
     * @return card response
     */
    CardResponse findCard(String cardNumber);

    /**
     * Carries the result of a register operation together with a flag
     * indicating whether the card already existed before this call.
     */
    record RegisterResult(CardResponse card, boolean alreadyExisted) {}
}