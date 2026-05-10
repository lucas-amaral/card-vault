package br.com.amaral.cardvault.exceptions;

import br.com.amaral.cardvault.entities.dto.response.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GlobalExceptionHandler}.
 */
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @InjectMocks
    private GlobalExceptionHandler handler;

    @Test
    @DisplayName("handleBadCredentials — returns 401 with failure message")
    void handleBadCredentials_returns401() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleBadCredentials(new BadCredentialsException("Bad credentials"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().message()).contains("Invalid username or password");
    }

    @Test
    @DisplayName("handleDisabled — returns 401 with disabled message")
    void handleDisabled_returns401() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleDisabled(new DisabledException("Account disabled"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().message()).contains("disabled");
    }

    @Test
    @DisplayName("handleNotFound — returns 404 with exception message")
    void handleNotFound_returns404() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleNotFound(new ResourceNotFoundException("Card not found"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().message()).isEqualTo("Card not found");
    }

    @Test
    @DisplayName("handleIllegalArgument — returns 400 with exception message")
    void handleIllegalArgument_returns400() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleIllegalArgument(new IllegalArgumentException("The uploaded file is empty"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).contains("empty");
    }

    @Test
    @DisplayName("handleFileSizeExceeded — returns 413")
    void handleFileSizeExceeded_returns413() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleFileSizeExceeded(new MaxUploadSizeExceededException(10_000_000));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody().message()).containsIgnoringCase("limit");
    }

    @Test
    @DisplayName("handleGeneric — returns 500 with generic message")
    void handleGeneric_returns500() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleGeneric(new RuntimeException("unexpected"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().message()).containsIgnoringCase("internal");
    }
}
