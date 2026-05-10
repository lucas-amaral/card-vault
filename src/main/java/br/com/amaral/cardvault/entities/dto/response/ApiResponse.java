package br.com.amaral.cardvault.entities.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Generic API response envelope for consistent response structure.
 *
 * @param <T> type of the response data payload
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Generic API response wrapper")
public record ApiResponse<T>(

        @Schema(description = "Whether the request succeeded")
        boolean success,

        @Schema(description = "Human-readable message")
        String message,

        @Schema(description = "Response payload")
        T data,

        @Schema(description = "Timestamp of the response")
        LocalDateTime timestamp
) {
    public static <T> ApiResponse<T> success(final T data, final String message) {
        return new ApiResponse<>(true, message, data, LocalDateTime.now());
    }

    public static <T> ApiResponse<T> error(final String message) {
        return new ApiResponse<>(false, message, null, LocalDateTime.now());
    }
}
