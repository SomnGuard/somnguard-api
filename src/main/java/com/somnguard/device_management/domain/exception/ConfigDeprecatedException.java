package com.somnguard.device_management.domain.exception;

/**
 * PATCH /devices/{id}/config está deprecated (ADR-011, solo global):
 * la configuración se gestiona vía catálogos y se versiona globalmente.
 * Mapea a 410 Gone.
 */
public class ConfigDeprecatedException extends RuntimeException {
    public ConfigDeprecatedException(String message) {
        super(message);
    }
}
