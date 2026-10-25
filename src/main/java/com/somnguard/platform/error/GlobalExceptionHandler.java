package com.somnguard.platform.error;

import com.somnguard.platform.security.FeatureAccessDeniedException;
import com.somnguard.telemetry_service.domain.exception.EvidenceConflictException;
import com.somnguard.telemetry_service.domain.exception.EvidenceStorageException;
import com.somnguard.telemetry_service.domain.exception.InvalidCatalogReferenceException;
import com.somnguard.telemetry_service.domain.exception.TelemetryEventNotFoundException;
import com.somnguard.device_management.domain.exception.ConfigDeprecatedException;
import com.somnguard.device_management.domain.exception.DeviceConflictException;
import com.somnguard.device_management.domain.exception.DeviceForbiddenException;
import com.somnguard.device_management.domain.exception.DeviceNotFoundException;
import com.somnguard.device_management.domain.exception.InvalidDeviceConfigException;
import com.somnguard.device_management.domain.exception.InvalidDeviceCredentialsException;
import com.somnguard.device_management.domain.exception.InvalidStatusTransitionException;
import com.somnguard.monitoring.domain.exception.NotificationNotFoundException;
import com.somnguard.security.domain.exception.DuplicateEmailException;
import com.somnguard.security.domain.exception.DuplicatePhoneException;
import com.somnguard.security.domain.exception.InvalidCredentialsException;
import com.somnguard.security.application.port.out.EmailSendException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Autowired(required = false)
    private HttpServletRequest request;

    public GlobalExceptionHandler() {}

    public GlobalExceptionHandler(HttpServletRequest request) {
        this.request = request;
    }

    private String resolveTraceId() {
        return resolveTraceId(this.request);
    }

    private String resolveTraceId(HttpServletRequest req) {
        if (req != null) {
            try {
                String traceHeader = req.getHeader("X-Trace-Id");
                if (traceHeader == null || traceHeader.isBlank()) {
                    traceHeader = req.getHeader("X-Request-Id");
                }
                if (traceHeader != null && !traceHeader.isBlank()) {
                    return traceHeader.trim();
                }
            } catch (Exception ignored) {}
        }
        String mdcTrace = MDC.get("traceId");
        if (mdcTrace != null && !mdcTrace.isBlank()) {
            return mdcTrace;
        }
        return UUID.randomUUID().toString();
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        String traceId = resolveTraceId();
        List<ErrorResponse.Detail> details = ex.getBindingResult().getAllErrors().stream()
                .map(err -> new ErrorResponse.Detail(((FieldError) err).getField(), err.getDefaultMessage()))
                .toList();
        LOG.debug("Validation error [trace_id={}]: {}", traceId, details);
        return ResponseEntity.badRequest().body(ErrorResponse.of("VALIDATION_ERROR", "Validación fallida", details, traceId));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        String traceId = resolveTraceId();
        return ResponseEntity.badRequest().body(ErrorResponse.of("BAD_REQUEST", ex.getMessage(), List.of(), traceId));
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateEmail(DuplicateEmailException ex) {
        String traceId = resolveTraceId();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of("EMAIL_CONFLICT", ex.getMessage(), List.of(), traceId));
    }

    @ExceptionHandler(DuplicatePhoneException.class)
    public ResponseEntity<ErrorResponse> handleDuplicatePhone(DuplicatePhoneException ex) {
        String traceId = resolveTraceId();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of("PHONE_CONFLICT", ex.getMessage(), List.of(), traceId));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException ex) {
        String traceId = resolveTraceId();
        LOG.warn("Invalid credentials attempt [trace_id={}]", traceId);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ErrorResponse.of("INVALID_CREDENTIALS", ex.getMessage(), List.of(), traceId));
    }

    @ExceptionHandler(FeatureAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleFeatureDenied(FeatureAccessDeniedException ex) {
        String traceId = resolveTraceId();
        LOG.warn("Feature access denied [trace_id={}]: {}", traceId, ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse.of("FORBIDDEN", ex.getMessage(), List.of(), traceId));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex) {
        String traceId = resolveTraceId();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of("CONFLICT", ex.getMessage(), List.of(), traceId));
    }

    @ExceptionHandler(DeviceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleDeviceNotFound(DeviceNotFoundException ex) {
        String traceId = resolveTraceId();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of("DEVICE_NOT_FOUND", ex.getMessage(), List.of(), traceId));
    }

    @ExceptionHandler(DeviceConflictException.class)
    public ResponseEntity<ErrorResponse> handleDeviceConflict(DeviceConflictException ex) {
        String traceId = resolveTraceId();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of("DEVICE_CONFLICT", ex.getMessage(), List.of(), traceId));
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(org.springframework.dao.DataIntegrityViolationException ex) {
        String traceId = resolveTraceId();
        LOG.warn("Data integrity violation [trace_id={}]: {}", traceId, ex.getMessage());
        String msg = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
        if (msg.contains("device_assignment") || msg.contains("assignment")) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of("DEVICE_CONFLICT", "Conflicto de unicidad (asignación duplicada)", List.of(), traceId));
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of("DATA_CONFLICT", "Conflicto de integridad de datos", List.of(), traceId));
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTransition(InvalidStatusTransitionException ex) {
        String traceId = resolveTraceId();
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ErrorResponse.of("INVALID_STATUS_TRANSITION", ex.getMessage(), List.of(), traceId));
    }

    @ExceptionHandler(InvalidDeviceCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleDeviceCredentials(InvalidDeviceCredentialsException ex) {
        String traceId = resolveTraceId();
        LOG.warn("Invalid device credentials [trace_id={}]", traceId);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ErrorResponse.of("INVALID_DEVICE_CREDENTIALS", ex.getMessage(), List.of(), traceId));
    }

    @ExceptionHandler(InvalidDeviceConfigException.class)
    public ResponseEntity<ErrorResponse> handleInvalidDeviceConfig(InvalidDeviceConfigException ex) {
        String traceId = resolveTraceId();
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ErrorResponse.of("INVALID_CONFIG_REFERENCE", ex.getMessage(), List.of(), traceId));
    }

    @ExceptionHandler(DeviceForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleDeviceForbidden(DeviceForbiddenException ex) {
        String traceId = resolveTraceId();
        LOG.warn("Device forbidden [trace_id={}]: {}", traceId, ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse.of("DEVICE_FORBIDDEN", ex.getMessage(), List.of(), traceId));
    }

    @ExceptionHandler(ConfigDeprecatedException.class)
    public ResponseEntity<ErrorResponse> handleConfigDeprecated(ConfigDeprecatedException ex) {
        String traceId = resolveTraceId();
        return ResponseEntity.status(HttpStatus.GONE).body(ErrorResponse.of("CONFIG_DEPRECATED", ex.getMessage(), List.of(), traceId));
    }

    @ExceptionHandler(InvalidCatalogReferenceException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCatalog(InvalidCatalogReferenceException ex) {
        String traceId = resolveTraceId();
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ErrorResponse.of("INVALID_CATALOG_REFERENCE", ex.getMessage(), List.of(), traceId));
    }

    @ExceptionHandler(TelemetryEventNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTelemetryNotFound(TelemetryEventNotFoundException ex) {
        String traceId = resolveTraceId();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of("TELEMETRY_EVENT_NOT_FOUND", ex.getMessage(), List.of(), traceId));
    }

    @ExceptionHandler(EvidenceConflictException.class)
    public ResponseEntity<ErrorResponse> handleEvidenceConflict(EvidenceConflictException ex) {
        String traceId = resolveTraceId();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of("EVIDENCE_CONFLICT", ex.getMessage(), List.of(), traceId));
    }

    @ExceptionHandler(EvidenceStorageException.class)
    public ResponseEntity<ErrorResponse> handleEvidenceStorage(EvidenceStorageException ex) {
        String traceId = resolveTraceId();
        LOG.error("Evidence storage error [trace_id={}]: {}", traceId, ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ErrorResponse.of("EVIDENCE_STORAGE_ERROR", ex.getMessage(), List.of(), traceId));
    }

    @ExceptionHandler(NotificationNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotificationNotFound(NotificationNotFoundException ex) {
        String traceId = resolveTraceId();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of("NOTIFICATION_NOT_FOUND", ex.getMessage(), List.of(), traceId));
    }

    @ExceptionHandler(EmailSendException.class)
    public ResponseEntity<ErrorResponse> handleEmailSend(EmailSendException ex) {
        String traceId = resolveTraceId();
        LOG.error("Email send error [trace_id={}]: {}", traceId, ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ErrorResponse.of("EMAIL_SEND_ERROR", ex.getMessage(), List.of(), traceId));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        String traceId = resolveTraceId();
        LOG.error("Unhandled server error [trace_id={}]: {}", traceId, ex.getMessage(), ex);
        return ResponseEntity.internalServerError().body(ErrorResponse.of("INTERNAL_ERROR", "Error interno del servidor", List.of(), traceId));
    }
}
