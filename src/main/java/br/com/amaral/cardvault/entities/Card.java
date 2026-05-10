package br.com.amaral.cardvault.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Stores an encrypted card number along with a hash for secure lookup.
 * The actual card number is never stored in plain text.
 */
@Entity
@Table(name = "cards")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Builder
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Public-facing UUID returned to clients as the unique identifier.
     */
    @Column(name = "external_id", nullable = false, unique = true, length = 36)
    private String externalId;

    /**
     * AES-256 encrypted card number. Never exposed in API responses.
     */
    @Column(name = "card_number_enc", nullable = false, columnDefinition = "TEXT")
    private String cardNumberEnc;

    /**
     * SHA-256 HMAC hash of the card number. Used for existence lookups.
     */
    @Column(name = "card_hash", nullable = false, unique = true, length = 64)
    private String cardHash;

    /**
     * Optional batch identifier when the card was imported via TXT file.
     */
    @Column(name = "batch_name", length = 100)
    private String batchName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}