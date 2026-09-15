package com.somnguard.platform.error;

import com.somnguard.platform.security.FeatureAccessDeniedException;
import com.somnguard.device_management.domain.exception.ConfigDeprecatedException;
import com.somnguard.device_management.domain.exception.DeviceConflictException;
import com.somnguard.device_management.domain.exception.DeviceForbiddenException;
import com.somnguard.device_management.domain.exception.DeviceNotFoundException;
import com.somnguard.device_management.domain.exception.InvalidDeviceConfigException;
import com.somnguard.device_management.domain.exception.InvalidDeviceCredentialsException;
import com.somnguard.device_management.domain.exception.InvalidStatusTransitionException;
import com.somnguard.security.domain.exception.DuplicateEmailException;
import com.somnguard.security.domain.exception.DuplicatePhoneException;
import com.somnguard.security.domain.exception.InvalidCredentialsException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.util.List;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private String traceId() { return UUID.randomUUID().toString(); }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        List<ErrorResponse.Detail> details = ex.getBindingResult().getAllErrors().stream()
                .map(err -> new ErrorResponse.Detail(((FieldError) err).getField(), err.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(ErrorResponse.of("VALIDATION_ERROR", "Validación fallida", details, traceId()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ErrorResponse.of("BAD_REQUEST", ex.getMessage(), List.of(), traceId()));
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateEmail(DuplicateEmailException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of("EMAIL_CONFLICT", ex.getMessage(), List.of(), traceId()));
    }

    @ExceptionHandler(DuplicatePhoneException.class)
    public ResponseEntity<ErrorResponse> handleDuplicatePhone(DuplicatePhoneException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of("PHONE_CONFLICT", ex.getMessage(), List.of(), traceId()));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ErrorResponse.of("INVALID_CREDENTIALS", ex.getMessage(), List.of(), traceId()));
    }

    @ExceptionHandler(FeatureAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleFeatureDenied(FeatureAccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse.of("FORBIDDEN", ex.getMessage(), List.of(), traceId()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of("CONFLICT", ex.getMessage(), List.of(), traceId()));
    }

    @ExceptionHandler(DeviceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleDeviceNotFound(DeviceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of("DEVICE_NOT_FOUND", ex.getMessage(), List.of(), traceId()));
    }

    @ExceptionHandler(DeviceConflictException.class)
    public ResponseEntity<ErrorResponse> handleDeviceConflict(DeviceConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of("DEVICE_CONFLICT", ex.getMessage(), List.of(), traceId()));
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(org.springframework.dao.DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of("DEVICE_CONFLICT", "Conflicto de unicidad (asignación duplicada)", List.of(), traceId()));
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTransition(InvalidStatusTransitionException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ErrorResponse.of("INVALID_STATUS_TRANSITION", ex.getMessage(), List.of(), traceId()));
    }

    @ExceptionHandler(InvalidDeviceCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleDeviceCredentials(InvalidDeviceCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ErrorResponse.of("INVALID_DEVICE_CREDENTIALS", ex.getMessage(), List.of(), traceId()));
    }

    @ExceptionHandler(InvalidDeviceConfigException.class)
    public ResponseEntity<ErrorResponse> handleInvalidDeviceConfig(InvalidDeviceConfigException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ErrorResponse.of("INVALID_CONFIG_REFERENCE", ex.getMessage(), List.of(), traceId()));
    }

    @ExceptionHandler(DeviceForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleDeviceForbidden(DeviceForbiddenException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse.of("DEVICE_FORBIDDEN", ex.getMessage(), List.of(), traceId()));
    }

    @ExceptionHandler(ConfigDeprecatedException.class)
    public ResponseEntity<ErrorResponse> handleConfigDeprecated(ConfigDeprecatedException ex) {
        return ResponseEntity.status(HttpStatus.GONE).body(ErrorResponse.of("CONFIG_DEPRECATED", ex.getMessage(), List.of(), traceId()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        return ResponseEntity.internalServerError().body(ErrorResponse.of("INTERNAL_ERROR", "Error interno del servidor", List.of(), traceId()));
    }
}