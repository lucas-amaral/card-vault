package br.com.amaral.cardvault.services;

import br.com.amaral.cardvault.entities.AuditLog;
import br.com.amaral.cardvault.repositories.AuditLogRepository;
import br.com.amaral.cardvault.utils.PayloadEncryptionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AuditLogService}.
 */
@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock private AuditLogRepository auditLogRepository;

    private AuditLogService auditLogService;
    private PayloadEncryptionUtil payloadEncryptionUtil;

    @BeforeEach
    void setUp() {
        payloadEncryptionUtil = new PayloadEncryptionUtil("0123456789abcdef0123456789abcdef");
        auditLogService = new AuditLogService(auditLogRepository, payloadEncryptionUtil);
    }

    @Test
    @DisplayName("save — encrypts request and response bodies before repository save")
    void save_encryptsBodiesBeforeRepositorySave() {
        AuditLog log = AuditLog.builder()
                .requestId("req-1")
                .httpMethod("POST")
                .endpoint("/api/v1/cards")
                .statusCode(201)
                .requestBody("{\"cardNumber\":\"4111111111111111\"}")
                .responseBody("{\"id\":\"card-id\"}")
                .build();

        auditLogService.save(log);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getRequestBody()).startsWith("enc:v1:");
        assertThat(saved.getResponseBody()).startsWith("enc:v1:");
        assertThat(saved.getRequestBody()).doesNotContain("4111111111111111");
        assertThat(saved.getResponseBody()).doesNotContain("card-id");
        assertThat(payloadEncryptionUtil.decrypt(saved.getRequestBody()))
                .isEqualTo("{\"cardNumber\":\"4111111111111111\"}");
        assertThat(payloadEncryptionUtil.decrypt(saved.getResponseBody()))
                .isEqualTo("{\"id\":\"card-id\"}");
    }

    @Test
    @DisplayName("save — null bodies remain null")
    void save_nullBodiesRemainNull() {
        AuditLog log = AuditLog.builder()
                .requestId("req-1")
                .httpMethod("GET")
                .endpoint("/api/v1/cards")
                .statusCode(200)
                .build();

        auditLogService.save(log);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        assertThat(captor.getValue().getRequestBody()).isNull();
        assertThat(captor.getValue().getResponseBody()).isNull();
    }

    @Test
    @DisplayName("save — repository exception is swallowed, does not propagate")
    void save_repositoryThrows_doesNotPropagate() {
        AuditLog log = AuditLog.builder().requestId("req-1").httpMethod("GET")
                .endpoint("/api/v1/cards/123").statusCode(200).build();

        doThrow(new RuntimeException("DB down")).when(auditLogRepository).save(any());

        // Must not throw
        auditLogService.save(log);
    }
}
