package com.somnguard.platform.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

class ApiKeyAuthenticationFilterTest {

    private final String apiKeyHeader = "X-API-Key";
    private final String deviceIdHeader = "X-Device-ID";
    private DeviceApiKeyValidator validator;
    private ApiKeyAuthenticationFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain filterChain;
    private StringWriter responseWriter;

    @BeforeEach
    void setUp() throws Exception {
        SecurityContextHolder.clearContext();
        validator = mock(DeviceApiKeyValidator.class);
        filter = new ApiKeyAuthenticationFilter(apiKeyHeader, deviceIdHeader, validator);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        filterChain = mock(FilterChain.class);
        responseWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilter_noHeaders_passesThroughWithoutAuthentication() throws Exception {
        when(request.getHeader(apiKeyHeader)).thenReturn(null);
        when(request.getHeader(deviceIdHeader)).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(validator);
    }

    @Test
    void doFilter_validHeadersAndValidKey_authenticatesDevice() throws Exception {
        UUID deviceId = UUID.randomUUID();
        String apiKey = "valid-api-key-12345";
        when(request.getHeader(deviceIdHeader)).thenReturn(deviceId.toString());
        when(request.getHeader(apiKeyHeader)).thenReturn(apiKey);
        when(validator.isValid(deviceId, apiKey)).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertTrue(auth.getPrincipal() instanceof ApiKeyAuthenticationFilter.DevicePrincipal);
        assertEquals(deviceId, ((ApiKeyAuthenticationFilter.DevicePrincipal) auth.getPrincipal()).deviceId());
        assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_DEVICE")));
    }

    @Test
    void doFilter_invalidApiKey_returns401AndHaltsChain() throws Exception {
        UUID deviceId = UUID.randomUUID();
        String apiKey = "invalid-api-key-99999";
        when(request.getHeader(deviceIdHeader)).thenReturn(deviceId.toString());
        when(request.getHeader(apiKeyHeader)).thenReturn(apiKey);
        when(validator.isValid(deviceId, apiKey)).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertTrue(responseWriter.toString().contains("UNAUTHORIZED"));
    }

    @Test
    void doFilter_malformedDeviceId_returns401AndHaltsChain() throws Exception {
        when(request.getHeader(deviceIdHeader)).thenReturn("not-a-uuid");
        when(request.getHeader(apiKeyHeader)).thenReturn("some-key");

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
