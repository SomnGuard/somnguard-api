package com.somnguard.monitoring.application.port.out;

import java.util.UUID;

/**
 * Puerto de salida push (HU-API-009 AC-002).
 * Desacoplado del proveedor (R-008/Q-008): hoy {@code LoggingPushSender},
 * mañana FCM/APNs sin tocar el dominio.
 */
public interface PushSender {

    PushResult send(UUID userId, String token, String title, String body);

    record PushResult(boolean ok, String providerMessageId, String error) {}
}
