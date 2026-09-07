package com.somnguard.device_management.domain.exception;

public class DeviceForbiddenException extends RuntimeException {
    public DeviceForbiddenException(String message) { super(message); }
}
