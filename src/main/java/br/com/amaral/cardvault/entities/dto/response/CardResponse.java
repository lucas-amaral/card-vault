package br.com.amaral.cardvault.entities.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Response payload returned when a card is registered or found.
 * Never exposes the actual card number.
 */
@Schema(description = "Card registration/lookup result")
public record CardResponse(

        @Schema(description = "Unique system identifier for this card (UUID)", example = "550e8400-e29b-41d4-a716-446655440000")
        String id,

        @Schema(description = "Batch name if imported via TXT file", example = "LOTE0001")
        String batchName,

        @Schema(description = "Timestamp when the card was registered")
        LocalDateTime createdAt
) {}
