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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Captures every HTTP request and response for audit purposes.
 *
 * <p><b>Security model for stored bodies:</b>
 * <ul>
 *   <li><b>Auth endpoints</b> — request body (credentials) and response body (JWT token)
 *       are fully suppressed. Storing passwords or bearer tokens in the database would
 *       allow credential replay attacks if the audit table is ever compromised.</li>
 *   <li><b>Card endpoints</b> — request and response bodies are fully suppressed because
 *       they carry raw PANs. Regex-based masking is intentionally not used here: it can
 *       be defeated by unusual formatting and gives a false sense of security.</li>
 *   <li><b>All other endpoints</b> — bodies are stored but scanned for digit sequences
 *       that resemble card numbers as a defence-in-depth second layer.</li>
 * </ul>
 *
 * <p>The audit record still captures method, URI, status code, duration and username,
 * which is sufficient for operational auditing without exposing sensitive payloads.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLoggingFilter extends OncePerRequestFilter {

    private static final int MAX_BODY_LENGTH = 2000;

    /**
     * URI prefixes whose request AND response bodies must be fully suppressed.
     * Add any new sensitive endpoint prefix here.
     */
    private static final Set<String> SUPPRESSED_PREFIXES = Set.of(
            "/api/v1/auth",   // credentials (request) and JWT token (response)
            "/api/v1/cards"   // raw PANs in request and response
    );

    private static final String SUPPRESSED = "[REDACTED]";

    /**
     * Defence-in-depth: masks digit sequences of 13-19 characters in non-suppressed
     * bodies. Matches numbers embedded inside JSON strings or separated by whitespace.
     */
    private static final Pattern PAN_PATTERN =
            Pattern.compile("(?<![\\d])(\\d{6})(\\d{3,9})(\\d{4})(?![\\d])");

    private final AuditLogService auditLogService;

    @Override
    protected void doFilterInternal(
            @NonNull final HttpServletRequest request,
            @NonNull final HttpServletResponse response,
            @NonNull final FilterChain filterChain
    ) throws ServletException, IOException {

        if (request.getRequestURI().startsWith("/actuator")) {
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
            final var uri = request.getRequestURI();
            final var isSuppressed = SUPPRESSED_PREFIXES.stream().anyMatch(uri::startsWith);

            final var requestBody = isSuppressed ? SUPPRESSED : sanitize(extractBody(wrappedRequest.getContentAsByteArray()));
            final var responseBody = isSuppressed ? SUPPRESSED : sanitize(extractBody(wrappedResponse.getContentAsByteArray()));

            log.info("[AUDIT] requestId={} method={} uri={} status={} user={} duration={}ms",
                    requestId, request.getMethod(), uri,
                    wrappedResponse.getStatus(), username, duration);

            auditLogService.save(AuditLog.builder()
                    .requestId(requestId)
                    .username(username)
                    .httpMethod(request.getMethod())
                    .endpoint(uri)
                    .statusCode(wrappedResponse.getStatus())
                    .requestBody(requestBody)
                    .responseBody(responseBody)
                    .ipAddress(request.getRemoteAddr())
                    .durationMs(duration)
                    .build());

            wrappedResponse.copyBodyToResponse();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String resolveUsername() {
        final var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }
        return "anonymous";
    }

    private String extractBody(final byte[] content) {
        if (content == null || content.length == 0) return null;
        final var body = new String(content, StandardCharsets.UTF_8);
        return body.length() > MAX_BODY_LENGTH
                ? body.substring(0, MAX_BODY_LENGTH) + "...[truncated]"
                : body;
    }

    /**
     * Masks any digit sequence that looks like a PAN (13-19 digits) found in
     * non-suppressed bodies. Keeps first 6 and last 4 digits visible, replaces
     * the middle with asterisks.
     */
    private String sanitize(final String body) {
        if (body == null) return null;
        return PAN_PATTERN.matcher(body).replaceAll(m ->
                m.group(1) + "*".repeat(m.group(2).length()) + m.group(3));
    }
}