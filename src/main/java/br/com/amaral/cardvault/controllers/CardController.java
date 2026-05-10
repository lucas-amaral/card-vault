package br.com.amaral.cardvault.controllers;

import br.com.amaral.cardvault.entities.dto.request.CardRequest;
import br.com.amaral.cardvault.entities.dto.response.ApiResponse;
import br.com.amaral.cardvault.entities.dto.response.BatchJobResponse;
import br.com.amaral.cardvault.entities.dto.response.CardResponse;
import br.com.amaral.cardvault.services.BatchProcessingService;
import br.com.amaral.cardvault.services.CardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Endpoints for card registration (single and async batch) and card lookup.
 * Contains no business logic — all decisions are delegated to the service layer.
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/cards")
@RequiredArgsConstructor
@Tag(name = "Cards", description = "Register and look up card numbers securely")
@SecurityRequirement(name = "bearerAuth")
public class CardController {

    private final CardService            cardService;
    private final BatchProcessingService batchProcessingService;

    @PostMapping
    @Operation(
            summary = "Register a single card number",
            description = "Accepts a single PAN and stores it encrypted. Returns the existing " +
                    "record with HTTP 200 if the card is already registered."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Card registered"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Card already exists"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid card number"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<ApiResponse<CardResponse>> registerCard(
            @Valid @RequestBody final CardRequest request,
            @AuthenticationPrincipal final UserDetails principal
    ) {
        final var result = cardService.registerCard(request.cardNumber(), principal.getUsername());

        if (result.alreadyExisted()) {
            return ResponseEntity.ok(ApiResponse.success(result.card(), "Card already registered"));
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(result.card(), "Card registered successfully"));
    }

    @PostMapping(value = "/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Bulk import cards from a TXT file (async)",
            description = "Accepts the proprietary TXT file and processes it asynchronously. " +
                    "Returns a jobId immediately. Poll GET /batch/{jobId}/status to track progress."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "Job accepted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid or empty file"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "413", description = "File too large")
    })
    public ResponseEntity<ApiResponse<BatchJobResponse>> uploadBatch(
            @Parameter(description = "TXT file in the Hyperativa batch format",
                    content = @Content(mediaType = "text/plain"))
            @RequestParam("file") final MultipartFile file,
            @AuthenticationPrincipal final UserDetails principal
    ) throws IOException {
        final var response = batchProcessingService.submit(file, principal.getUsername());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(response,
                        "Batch job accepted. Poll /api/v1/cards/batch/" + response.jobId() + "/status for progress."));
    }

    @GetMapping("/batch/{jobId}/status")
    @Operation(
            summary = "Poll batch job status",
            description = "Returns the current status of an async batch import job: PENDING, PROCESSING, DONE or FAILED."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Status returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Job not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<ApiResponse<BatchJobResponse>> getBatchStatus(
            @Parameter(description = "Job ID returned by the batch upload endpoint")
            @PathVariable String jobId
    ) {
        final var response = batchProcessingService.getStatus(jobId);
        return ResponseEntity.ok(ApiResponse.success(response, "Job status retrieved"));
    }

    @GetMapping("/{cardNumber}")
    @Operation(
            summary = "Check if a card number exists",
            description = "Returns the unique system identifier (UUID) for the given card number if found."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Card found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Card not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<ApiResponse<CardResponse>> findCard(
            @Parameter(description = "Full card number (PAN) to look up", example = "4456897999999999")
            @PathVariable
            @Pattern(regexp = "\\d{13,19}", message = "Card number must contain 13 to 19 digits")
            final String cardNumber
    ) {
        final var response = cardService.findCard(cardNumber);
        return ResponseEntity.ok(ApiResponse.success(response, "Card found"));
    }
}