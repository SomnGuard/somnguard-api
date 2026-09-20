package com.somnguard.telemetry_service.domain.exception;

/**
 * Fallo contra el object storage (MinIO caído, credenciales, red).
 * Debe ser 5xx para que el edge lo trate como reintentable (un 409 haría que
 * el device borre la evidencia local creyéndola duplicada).
 */
public class EvidenceStorageException extends RuntimeException {
    public EvidenceStorageException(String message, Throwable cause) { super(message, cause); }
}
