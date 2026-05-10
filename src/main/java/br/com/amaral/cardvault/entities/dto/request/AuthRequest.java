package br.com.amaral.cardvault.entities.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for user authentication.
 */
@Schema(description = "Credentials for obtaining a JWT token")
public record AuthRequest(

        @NotBlank(message = "Username is required")
        @Size(max = 100)
        @Schema(description = "Username", example = "admin")
        String username,

        @NotBlank(message = "Password is required")
        @Schema(description = "Password", example = "Admin@123")
        String password
) {}
