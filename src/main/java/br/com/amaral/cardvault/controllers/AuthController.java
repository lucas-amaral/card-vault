package br.com.amaral.cardvault.controllers;

import br.com.amaral.cardvault.entities.dto.request.AuthRequest;
import br.com.amaral.cardvault.entities.dto.response.ApiResponse;
import br.com.amaral.cardvault.entities.dto.response.AuthResponse;
import br.com.amaral.cardvault.services.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Handles user authentication and JWT token issuance.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Obtain a JWT token to access protected endpoints")
public class AuthController {

    private final AuthService authService;

    /**
     * Authenticates the user and returns a JWT token.
     *
     * @param request credentials
     * @return JWT token
     */
    @PostMapping("/login")
    @Operation(
            summary = "Authenticate user",
            description = "Validates credentials and returns a Bearer JWT token valid for 24 hours"
    )
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody final AuthRequest request) {
        AuthResponse authResponse = authService.authenticate(request);
        return ResponseEntity.ok(ApiResponse.success(authResponse, "Authentication successful"));
    }
}
