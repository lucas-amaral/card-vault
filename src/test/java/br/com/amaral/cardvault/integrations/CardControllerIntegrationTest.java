package br.com.amaral.cardvault.integrations;

import br.com.amaral.cardvault.entities.dto.request.AuthRequest;
import br.com.amaral.cardvault.entities.dto.request.CardRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for card registration and lookup endpoints.
 */
class CardControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String jwtToken;

    @BeforeEach
    void authenticate() throws Exception {
        AuthRequest authRequest = new AuthRequest("admin", "Admin@123");
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(authRequest)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        jwtToken = body.path("data").path("accessToken").asText();
    }

    @Test
    @DisplayName("POST /api/v1/cards — registers a new card and returns 201")
    void registerCard_newCard_returns201() throws Exception {
        CardRequest request = new CardRequest("5168441223630339");

        mockMvc.perform(post("/api/v1/cards")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id", notNullValue()));
    }

    @Test
    @DisplayName("POST /api/v1/cards — duplicate card returns 200 with existing record")
    void registerCard_duplicate_returns200() throws Exception {
        CardRequest request = new CardRequest("4111111111111111");

        // First insert
        mockMvc.perform(post("/api/v1/cards")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Duplicate insert
        mockMvc.perform(post("/api/v1/cards")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Card already registered"));
    }

    @Test
    @DisplayName("POST /api/v1/cards — invalid card number returns 400")
    void registerCard_invalidNumber_returns400() throws Exception {
        CardRequest request = new CardRequest("123"); // too short

        mockMvc.perform(post("/api/v1/cards")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/cards — no token returns 401")
    void registerCard_noToken_returns401() throws Exception {
        CardRequest request = new CardRequest("4111111111111111");

        mockMvc.perform(post("/api/v1/cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/cards/{cardNumber} — existing card returns 200 with UUID")
    void findCard_exists_returns200() throws Exception {
        String cardNumber = "4916338506082832";
        CardRequest request = new CardRequest(cardNumber);

        // Register first
        mockMvc.perform(post("/api/v1/cards")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Then look up
        mockMvc.perform(get("/api/v1/cards/" + cardNumber)
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", notNullValue()));
    }

    @Test
    @DisplayName("GET /api/v1/cards/{cardNumber} — non-existent card returns 404")
    void findCard_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/cards/9999999999999999")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/v1/cards/batch — valid TXT file is processed successfully")
    void uploadBatch_validFile_returns200() throws Exception {
        String txtContent = """
                DESAFIO-HYPERATIVA 20180524LOTE0001000010
                C1 4456897999999999
                C2 4456897922969001
                C3 4456897999009999
                LOTE0001000003
                """;

        MockMultipartFile file = new MockMultipartFile(
                "file", "DESAFIO-HYPERATIVA.txt", "text/plain", txtContent.getBytes()
        );

        mockMvc.perform(multipart("/api/v1/cards/batch")
                        .file(file)
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalParsed").isNumber())
                .andExpect(jsonPath("$.data.inserted").isNumber());
    }
}
