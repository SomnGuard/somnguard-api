package com.somnguard.platform.error;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTraceTest {

    @Test
    void unhandledException_usesTraceIdFromHeader_andReturns500() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        String expectedTraceId = "trace-test-uuid-12345";
        when(request.getHeader("X-Trace-Id")).thenReturn(expectedTraceId);

        GlobalExceptionHandler handler = new GlobalExceptionHandler(request);
        Exception unhandled = new RuntimeException("Simulated crash");

        ResponseEntity<ErrorResponse> response = handler.handleGeneric(unhandled);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INTERNAL_ERROR", response.getBody().error().code());
        assertEquals(expectedTraceId, response.getBody().error().trace_id());
    }

    @Test
    void unhandledException_generatesTraceId_whenHeaderAbsent() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Trace-Id")).thenReturn(null);
        when(request.getHeader("X-Request-Id")).thenReturn(null);

        GlobalExceptionHandler handler = new GlobalExceptionHandler(request);
        Exception unhandled = new RuntimeException("Simulated crash");

        ResponseEntity<ErrorResponse> response = handler.handleGeneric(unhandled);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().error().trace_id());
        assertFalse(response.getBody().error().trace_id().isBlank());
    }
}
