package br.com.amaral.cardvault.controllers;

import br.com.amaral.cardvault.entities.dto.request.CardRequest;
import br.com.amaral.cardvault.entities.dto.response.BatchJobResponse;
import br.com.amaral.cardvault.entities.dto.response.CardResponse;
import br.com.amaral.cardvault.services.BatchProcessingService;
import br.com.amaral.cardvault.services.CardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CardController}.
 */
@ExtendWith(MockitoExtension.class)
class CardControllerTest {

    @Mock private CardService cardService;
    @Mock private BatchProcessingService batchProcessingService;

    @InjectMocks
    private CardController cardController;

    private UserDetails principal;

    @BeforeEach
    void setUp() {
        principal = User.builder()
                .username("admin")
                .password("hashed")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .build();
    }

    @Test
    @DisplayName("registerCard — new card returns 201 Created")
    void registerCard_newCard_returnsCreated() {
        CardResponse cardResponse = new CardResponse("card-id", null, LocalDateTime.now());
        when(cardService.registerCard("4111111111111111", "admin"))
                .thenReturn(new CardService.RegisterResult(cardResponse, false));

        var response = cardController.registerCard(new CardRequest("4111111111111111"), principal);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().message()).isEqualTo("Card registered successfully");
        assertThat(response.getBody().data()).isEqualTo(cardResponse);
        verify(cardService).registerCard("4111111111111111", "admin");
    }

    @Test
    @DisplayName("registerCard — existing card returns 200 OK")
    void registerCard_existingCard_returnsOk() {
        CardResponse cardResponse = new CardResponse("card-id", null, LocalDateTime.now());
        when(cardService.registerCard("4111111111111111", "admin"))
                .thenReturn(new CardService.RegisterResult(cardResponse, true));

        var response = cardController.registerCard(new CardRequest("4111111111111111"), principal);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Card already registered");
        assertThat(response.getBody().data()).isEqualTo(cardResponse);
    }

    @Test
    @DisplayName("findCard — returns card response wrapped in ApiResponse")
    void findCard_returnsCardResponse() {
        CardResponse cardResponse = new CardResponse("card-id", "LOTE0001", LocalDateTime.now());
        when(cardService.findCard("4111111111111111")).thenReturn(cardResponse);

        var response = cardController.findCard("4111111111111111");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().message()).isEqualTo("Card found");
        assertThat(response.getBody().data()).isEqualTo(cardResponse);
        verify(cardService).findCard("4111111111111111");
    }

    @Test
    @DisplayName("uploadBatch — returns 202 Accepted with job response")
    void uploadBatch_returnsAcceptedJobResponse() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "cards.txt",
                "text/plain",
                "header\n4111111111111111\n".getBytes()
        );
        BatchJobResponse batchResponse = new BatchJobResponse(
                "job-id",
                "PENDING",
                "cards.txt",
                null,
                null,
                null,
                null,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        when(batchProcessingService.submit(file, "admin")).thenReturn(batchResponse);

        var response = cardController.uploadBatch(file, principal);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().data()).isEqualTo(batchResponse);
        assertThat(response.getBody().message()).contains("job-id");
        verify(batchProcessingService).submit(file, "admin");
    }

    @Test
    @DisplayName("getBatchStatus — returns job status wrapped in ApiResponse")
    void getBatchStatus_returnsJobStatus() {
        BatchJobResponse batchResponse = new BatchJobResponse(
                "job-id",
                "DONE",
                "cards.txt",
                10,
                8,
                2,
                null,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        when(batchProcessingService.getStatus("job-id")).thenReturn(batchResponse);

        var response = cardController.getBatchStatus("job-id");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Job status retrieved");
        assertThat(response.getBody().data()).isEqualTo(batchResponse);
        verify(batchProcessingService).getStatus("job-id");
    }
}
