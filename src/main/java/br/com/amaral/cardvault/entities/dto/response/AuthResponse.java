package br.com.amaral.cardvault.entities.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response payload containing the JWT token issued after successful authentication.
 */
@Schema(description = "JWT authentication token response")
public record AuthResponse(

        @Schema(description = "JWT access token")
        String accessToken,

        @Schema(description = "Token type", example = "Bearer")
        String tokenType,

        @Schema(description = "Expiration time in milliseconds")
        long expiresIn
) {
    public AuthResponse(final String accessToken, final long expiresIn) {
        this(accessToken, "Bearer", expiresIn);
    }
}
