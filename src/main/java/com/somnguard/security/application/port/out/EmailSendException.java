package com.somnguard.security.application.port.out;

/**
 * Falla el envío de un correo. Nunca incluye secretos
 * (API keys, credenciales, headers de autorización).
 */
public class EmailSendException extends RuntimeException {

    public EmailSendException(String message) {
        super(message);
    }

    public EmailSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
