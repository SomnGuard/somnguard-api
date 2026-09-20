package com.somnguard.telemetry_service.domain.exception;

public class TelemetryEventNotFoundException extends RuntimeException {
    public TelemetryEventNotFoundException(String message) { super(message); }
}
