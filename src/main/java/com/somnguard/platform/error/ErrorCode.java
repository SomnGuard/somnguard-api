package com.somnguard.platform.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    // Generic
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred"),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Validation failed"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
    CONFLICT(HttpStatus.CONFLICT, "Resource conflict"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication required"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Access denied"),
    UNPROCESSABLE_ENTITY(HttpStatus.UNPROCESSABLE_ENTITY, "Unprocessable entity"),

    // Security
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid credentials"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Token expired"),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Invalid token"),
    ACCOUNT_LOCKED(HttpStatus.FORBIDDEN, "Account locked"),
    PASSWORD_RESET_EXPIRED(HttpStatus.BAD_REQUEST, "Password reset token expired"),
    PASSWORD_RESET_USED(HttpStatus.BAD_REQUEST, "Password reset token already used"),

    // Device
    DEVICE_NOT_FOUND(HttpStatus.NOT_FOUND, "Device not found"),
    DEVICE_ALREADY_ASSIGNED(HttpStatus.CONFLICT, "Device already assigned"),
    DEVICE_NOT_ASSIGNED(HttpStatus.CONFLICT, "Device not assigned to user"),
    INVALID_API_KEY(HttpStatus.UNAUTHORIZED, "Invalid device API key"),

    // Telemetry
    EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Event not found"),
    EVIDENCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Evidence not found"),
    INVALID_EVENT_PAYLOAD(HttpStatus.BAD_REQUEST, "Invalid event payload"),

    // Parameterization
    CATALOG_NOT_FOUND(HttpStatus.NOT_FOUND, "Catalog entry not found"),
    CATALOG_CODE_EXISTS(HttpStatus.CONFLICT, "Catalog code already exists"),

    // Monitoring
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Notification not found");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}