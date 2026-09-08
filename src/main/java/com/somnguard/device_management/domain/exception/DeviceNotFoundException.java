package com.somnguard.device_management.domain.exception;

public class DeviceNotFoundException extends RuntimeException {
    public DeviceNotFoundException(String message) { super(message); }
}
