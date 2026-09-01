package com.somnguard.platform.error;

import com.somnguard.platform.security.FeatureAccessDeniedException;
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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        return ResponseEntity.internalServerError().body(ErrorResponse.of("INTERNAL_ERROR", "Error interno del servidor", List.of(), traceId()));
    }
}