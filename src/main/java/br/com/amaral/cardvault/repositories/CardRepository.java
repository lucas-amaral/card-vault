package br.com.amaral.cardvault.repositories;

import br.com.amaral.cardvault.entities.Card;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Data access layer for {@link Card} entities.
 */
@Repository
public interface CardRepository extends JpaRepository<Card, Long> {

    Optional<Card> findByCardHash(String cardHash);

    Optional<Card> findByExternalId(String externalId);

    boolean existsByCardHash(String cardHash);

    List<Card> findByBatchName(String batchName);
}