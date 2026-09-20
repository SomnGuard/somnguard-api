package com.somnguard.telemetry_service.domain.exception;

public class InvalidCatalogReferenceException extends RuntimeException {
    public InvalidCatalogReferenceException(String message) { super(message); }
}
