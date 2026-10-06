package com.somnguard.monitoring.domain.exception;

import java.util.UUID;

public class NotificationNotFoundException extends RuntimeException {

    public NotificationNotFoundException(UUID id) {
        super("Notificación no encontrada: " + id);
    }
}
