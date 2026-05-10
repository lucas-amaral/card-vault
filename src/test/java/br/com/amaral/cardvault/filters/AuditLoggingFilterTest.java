package br.com.amaral.cardvault.filters;

import br.com.amaral.cardvault.entities.AuditLog;
import br.com.amaral.cardvault.services.AuditLogService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link AuditLoggingFilter} focusing on sensitive data suppression.
 */
@ExtendWith(MockitoExtension.class)
class AuditLoggingFilterTest {

    @Mock private AuditLogService auditLogService;
    @Mock private FilterChain filterChain;

    @InjectMocks
    private AuditLoggingFilter filter;

    private ArgumentCaptor<AuditLog> logCaptor;
    private int downstreamStatus;
    private String downstreamResponseBody;

    @BeforeEach
    void setUp() throws Exception {
        logCaptor = ArgumentCaptor.forClass(AuditLog.class);
        downstreamStatus = 200;
        downstreamResponseBody = null;
        doAnswer(invocation -> {
            HttpServletRequest request = invocation.getArgument(0);
            HttpServletResponse response = invocation.getArgument(1);

            request.getInputStream().readAllBytes();
            response.setStatus(downstreamStatus);
            if (downstreamResponseBody != null) {
                response.getWriter().write(downstreamResponseBody);
                response.getWriter().flush();
            }
            return null;
        }).when(filterChain).doFilter(org.mockito.Mockito.any(), org.mockito.Mockito.any());
    }

    // -------------------------------------------------------------------------
    // Auth endpoint suppression
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/v1/auth/login — request and response bodies are fully suppressed")
    void authEndpoint_bodiesAreSuppressed() throws Exception {
        MockHttpServletRequest request = buildRequest(
                "POST", "/api/v1/auth/login",
                "{\"username\":\"admin\",\"password\":\"Admin@123\"}");
        MockHttpServletResponse response = buildResponse(
                200, "{\"data\":{\"accessToken\":\"eyJhbGciOiJIUzI1NiJ9.secret\"}}");

        filter.doFilterInternal(request, response, filterChain);

        verify(auditLogService).save(logCaptor.capture());
        AuditLog saved = logCaptor.getValue();

        assertThat(saved.getRequestBody()).isEqualTo("[REDACTED]");
        assertThat(saved.getResponseBody()).isEqualTo("[REDACTED]");
        assertThat(saved.getRequestBody()).doesNotContain("Admin@123");
        assertThat(saved.getResponseBody()).doesNotContain("eyJhbGciOiJIUzI1NiJ9");
    }

    // -------------------------------------------------------------------------
    // Card endpoint suppression
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/v1/cards — request body with PAN is fully suppressed")
    void cardEndpoint_singleCard_bodySuppressed() throws Exception {
        MockHttpServletRequest request = buildRequest(
                "POST", "/api/v1/cards",
                "{\"cardNumber\":\"4111111111111111\"}");
        MockHttpServletResponse response = buildResponse(
                201, "{\"data\":{\"id\":\"some-uuid\"}}");

        filter.doFilterInternal(request, response, filterChain);

        verify(auditLogService).save(logCaptor.capture());
        AuditLog saved = logCaptor.getValue();

        assertThat(saved.getRequestBody()).isEqualTo("[REDACTED]");
        assertThat(saved.getResponseBody()).isEqualTo("[REDACTED]");
        assertThat(saved.getRequestBody()).doesNotContain("4111111111111111");
    }

    @Test
    @DisplayName("GET /api/v1/cards/{pan} — response body is fully suppressed")
    void cardEndpoint_lookup_bodySuppressed() throws Exception {
        MockHttpServletRequest request = buildRequest(
                "GET", "/api/v1/cards/4111111111111111", null);
        MockHttpServletResponse response = buildResponse(
                200, "{\"data\":{\"id\":\"some-uuid\",\"cardNumber\":\"4111111111111111\"}}");

        filter.doFilterInternal(request, response, filterChain);

        verify(auditLogService).save(logCaptor.capture());
        AuditLog saved = logCaptor.getValue();

        assertThat(saved.getRequestBody()).isEqualTo("[REDACTED]");
        assertThat(saved.getResponseBody()).isEqualTo("[REDACTED]");
    }

    @Test
    @DisplayName("POST /api/v1/cards/batch — bodies are fully suppressed")
    void cardEndpoint_batch_bodySuppressed() throws Exception {
        MockHttpServletRequest request = buildRequest(
                "POST", "/api/v1/cards/batch", null);
        MockHttpServletResponse response = buildResponse(
                202, "{\"data\":{\"jobId\":\"some-uuid\",\"status\":\"PENDING\"}}");

        filter.doFilterInternal(request, response, filterChain);

        verify(auditLogService).save(logCaptor.capture());
        AuditLog saved = logCaptor.getValue();

        assertThat(saved.getRequestBody()).isEqualTo("[REDACTED]");
        assertThat(saved.getResponseBody()).isEqualTo("[REDACTED]");
    }

    // -------------------------------------------------------------------------
    // Non-sensitive endpoints — defence-in-depth masking
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Non-sensitive endpoint — body without PAN is stored as-is")
    void nonSensitiveEndpoint_noPan_storedAsIs() throws Exception {
        MockHttpServletRequest request = buildRequest(
                "GET", "/api/v1/some-other-endpoint", null);
        MockHttpServletResponse response = buildResponse(200, "{\"status\":\"ok\"}");

        filter.doFilterInternal(request, response, filterChain);

        verify(auditLogService).save(logCaptor.capture());
        AuditLog saved = logCaptor.getValue();

        assertThat(saved.getResponseBody()).contains("ok");
        assertThat(saved.getResponseBody()).isNotEqualTo("[REDACTED]");
    }

    @Test
    @DisplayName("Non-sensitive endpoint — embedded PAN-like number is masked by defence-in-depth")
    void nonSensitiveEndpoint_withEmbeddedPan_isMasked() throws Exception {
        MockHttpServletRequest request = buildRequest(
                "GET", "/api/v1/some-other-endpoint", null);
        MockHttpServletResponse response = buildResponse(
                200, "{\"note\":\"ref 4111111111111111 processed\"}");

        filter.doFilterInternal(request, response, filterChain);

        verify(auditLogService).save(logCaptor.capture());
        AuditLog saved = logCaptor.getValue();

        assertThat(saved.getResponseBody()).doesNotContain("4111111111111111");
        assertThat(saved.getResponseBody()).contains("411111");   // first 6 visible
        assertThat(saved.getResponseBody()).contains("1111");     // last 4 visible
        assertThat(saved.getResponseBody()).contains("***");      // middle masked
    }

    // -------------------------------------------------------------------------
    // Metadata is always recorded
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Audit record always contains method, URI, status and duration regardless of suppression")
    void auditRecord_alwaysContainsMetadata() throws Exception {
        MockHttpServletRequest request = buildRequest(
                "POST", "/api/v1/auth/login",
                "{\"username\":\"admin\",\"password\":\"secret\"}");
        MockHttpServletResponse response = buildResponse(200, "{\"token\":\"abc\"}");

        filter.doFilterInternal(request, response, filterChain);

        verify(auditLogService).save(logCaptor.capture());
        AuditLog saved = logCaptor.getValue();

        assertThat(saved.getHttpMethod()).isEqualTo("POST");
        assertThat(saved.getEndpoint()).isEqualTo("/api/v1/auth/login");
        assertThat(saved.getStatusCode()).isEqualTo(200);
        assertThat(saved.getDurationMs()).isNotNull().isGreaterThanOrEqualTo(0);
        assertThat(saved.getRequestId()).isNotBlank();
    }

    @Test
    @DisplayName("Actuator endpoints are skipped entirely — no audit record saved")
    void actuatorEndpoint_isSkipped() throws Exception {
        MockHttpServletRequest request = buildRequest("GET", "/actuator/health", null);
        MockHttpServletResponse response = buildResponse(200, "{\"status\":\"UP\"}");

        filter.doFilterInternal(request, response, filterChain);

        verify(auditLogService, org.mockito.Mockito.never()).save(org.mockito.Mockito.any());
    }

    @Test
    @DisplayName("Swagger UI and OpenAPI endpoints are skipped entirely — no audit record saved")
    void documentationEndpoints_areSkipped() throws Exception {
        String[] endpoints = {
                "/swagger-ui/index.html",
                "/swagger-ui/swagger-ui-standalone-preset.js",
                "/swagger-ui/swagger-initializer.js",
                "/swagger-ui/index.css",
                "/swagger-ui/swagger-ui.css",
                "/swagger-ui/swagger-ui-bundle.js",
                "/api-docs/swagger-config",
                "/api-docs"
        };

        for (String endpoint : endpoints) {
            MockHttpServletRequest request = buildRequest("GET", endpoint, null);
            MockHttpServletResponse response = buildResponse(200, null);

            filter.doFilterInternal(request, response, filterChain);
        }

        verify(auditLogService, org.mockito.Mockito.never()).save(org.mockito.Mockito.any());
    }

    @Test
    @DisplayName("OPTIONS preflight requests are skipped entirely — no audit record saved")
    void optionsPreflight_isSkipped() throws Exception {
        MockHttpServletRequest request = buildRequest("OPTIONS", "/api/v1/cards", null);
        MockHttpServletResponse response = buildResponse(200, null);

        filter.doFilterInternal(request, response, filterChain);

        verify(auditLogService, org.mockito.Mockito.never()).save(org.mockito.Mockito.any());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private MockHttpServletRequest buildRequest(String method, String uri, String body) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        if (body != null) {
            request.setContent(body.getBytes());
            request.setContentType("application/json");
        }
        return request;
    }

    private MockHttpServletResponse buildResponse(int status, String body) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        downstreamStatus = status;
        downstreamResponseBody = body;
        return response;
    }
}
