package br.com.amaral.cardvault.filters;

import br.com.amaral.cardvault.entities.AuditLog;
import br.com.amaral.cardvault.services.AuditLogService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Captures every HTTP request and response for audit purposes.
 * Card numbers are masked before storage to avoid leaking sensitive data in logs.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLoggingFilter extends OncePerRequestFilter {

    private static final int MAX_BODY_LENGTH = 2000;
    private static final String[] SKIPPED_ENDPOINT_PREFIXES = {
            "/actuator",
            "/swagger-ui",
            "/api-docs"
    };
    private static final String[] SKIPPED_ENDPOINTS = {
            "/favicon.ico",
            "/error"
    };

    private final AuditLogService auditLogService;

    @Override
    protected void doFilterInternal(
            @NonNull final HttpServletRequest request,
            @NonNull final HttpServletResponse response,
            @NonNull final FilterChain filterChain
    ) throws ServletException, IOException {

        if (shouldSkipAudit(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        final var wrappedRequest = new ContentCachingRequestWrapper(request, MAX_BODY_LENGTH);
        final var wrappedResponse = new ContentCachingResponseWrapper(response);

        final var requestId = UUID.randomUUID().toString();
        final var startTime = System.currentTimeMillis();

        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);
        } finally {
            final var duration = System.currentTimeMillis() - startTime;
            final var username = resolveUsername();
            final var requestBody = extractBody(wrappedRequest.getContentAsByteArray());
            final var responseBody = extractBody(wrappedResponse.getContentAsByteArray());
            final var redactedEndpoint = shouldRedactBodies(request.getRequestURI());

            log.info("[AUDIT] requestId={} method={} uri={} status={} user={} duration={}ms",
                    requestId,
                    request.getMethod(),
                    request.getRequestURI(),
                    wrappedResponse.getStatus(),
                    username,
                    duration);

            final var auditLog = AuditLog.builder()
                    .requestId(requestId)
                    .username(username)
                    .httpMethod(request.getMethod())
                    .endpoint(request.getRequestURI())
                    .statusCode(wrappedResponse.getStatus())
                    .requestBody(redactedEndpoint ? "[REDACTED]" : maskSensitiveData(requestBody))
                    .responseBody(redactedEndpoint ? "[REDACTED]" : maskSensitiveData(responseBody))
                    .ipAddress(request.getRemoteAddr())
                    .durationMs(duration)
                    .build();

            auditLogService.save(auditLog);

            wrappedResponse.copyBodyToResponse();
        }
    }

    private String resolveUsername() {
        final var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }
        return "anonymous";
    }

    private String extractBody(byte[] content) {
        if (content == null || content.length == 0) {
            return null;
        }
        final var body = new String(content, StandardCharsets.UTF_8);
        return body.length() > MAX_BODY_LENGTH ? body.substring(0, MAX_BODY_LENGTH) + "...[truncated]" : body;
    }

    private boolean shouldSkipAudit(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        final var uri = request.getRequestURI();
        for (String skippedEndpoint : SKIPPED_ENDPOINTS) {
            if (skippedEndpoint.equals(uri)) {
                return true;
            }
        }
        for (String skippedPrefix : SKIPPED_ENDPOINT_PREFIXES) {
            if (uri.startsWith(skippedPrefix)) {
                return true;
            }
        }
        return false;
    }

    private boolean shouldRedactBodies(String uri) {
        return uri.startsWith("/api/v1/auth");
    }

    /**
     * Replaces the value of any JSON field named "cardNumber" with a masked version,
     * and also masks bare digit sequences that look like card numbers (13–19 digits).
     * This keeps the rest of the audit payload intact and readable.
     */
    private String maskSensitiveData(String body) {
        if (body == null) return null;
        // Mask "cardNumber":"<digits>" in JSON bodies
        String masked = body.replaceAll(
                "(?i)(\"cardNumber\"\\s*:\\s*\")(\\d{6})\\d+(\\d{4})(\")",
                "$1$2****$3$4"
        );
        // Also mask any bare PAN-like digit sequences not already caught above
        return masked.replaceAll("(?<!\\d)(\\d{6})\\d{3,9}(\\d{4})(?!\\d)", "$1****$2");
    }
}