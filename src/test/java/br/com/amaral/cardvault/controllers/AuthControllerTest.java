package br.com.amaral.cardvault.controllers;

import br.com.amaral.cardvault.entities.dto.request.AuthRequest;
import br.com.amaral.cardvault.entities.dto.response.AuthResponse;
import br.com.amaral.cardvault.services.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AuthController}.
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock private AuthService authService;

    @InjectMocks
    private AuthController authController;

    @Test
    @DisplayName("login — returns authenticated token wrapped in ApiResponse")
    void login_returnsAuthenticatedToken() {
        AuthRequest request = new AuthRequest("admin", "Admin@123");
        AuthResponse authResponse = new AuthResponse("jwt-token", 86400000L);
        when(authService.authenticate(request)).thenReturn(authResponse);

        var response = authController.login(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().message()).isEqualTo("Authentication successful");
        assertThat(response.getBody().data()).isEqualTo(authResponse);
        assertThat(response.getBody().data().tokenType()).isEqualTo("Bearer");
        verify(authService).authenticate(request);
    }
}
