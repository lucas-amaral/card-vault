package br.com.amaral.cardvault.entities.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request payload for inserting a single card number.
 */
@Schema(description = "Payload for registering a single card number")
public record CardRequest(

        @NotBlank(message = "Card number is required")
        @Pattern(regexp = "\\d{13,19}", message = "Card number must contain 13 to 19 digits")
        @Schema(description = "Full card number (PAN)", example = "4456897999999999")
        String cardNumber
) {}
