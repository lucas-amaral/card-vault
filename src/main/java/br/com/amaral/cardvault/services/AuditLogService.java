package br.com.amaral.cardvault.services;

import br.com.amaral.cardvault.entities.AuditLog;
import br.com.amaral.cardvault.repositories.AuditLogRepository;
import br.com.amaral.cardvault.utils.PayloadEncryptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Persists API request/response audit records asynchronously to avoid
 * blocking the main request thread.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final PayloadEncryptionUtil payloadEncryptionUtil;

    /**
     * Saves an audit log entry asynchronously.
     *
     * @param auditLog the populated audit record
     */
    @Async
    public void save(final AuditLog auditLog) {
        try {
            auditLog.setRequestBody(payloadEncryptionUtil.encrypt(auditLog.getRequestBody()));
            auditLog.setResponseBody(payloadEncryptionUtil.encrypt(auditLog.getResponseBody()));
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            // Never let audit failures propagate to the caller
            log.error("Failed to persist audit log entry: requestId={}", auditLog.getRequestId(), e);
        }
    }
}
