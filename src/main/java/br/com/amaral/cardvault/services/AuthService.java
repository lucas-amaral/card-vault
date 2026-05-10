package br.com.amaral.cardvault.services;

import br.com.amaral.cardvault.entities.dto.request.AuthRequest;
import br.com.amaral.cardvault.entities.dto.response.AuthResponse;

/**
 * Handles user authentication and token issuance.
 */
public interface AuthService {

    /**
     * Authenticates the user and returns a JWT token.
     *
     * @param request credentials
     * @return JWT token response
     */
    AuthResponse authenticate(AuthRequest request);
}
