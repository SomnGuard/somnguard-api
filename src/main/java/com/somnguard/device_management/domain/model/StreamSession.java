package com.somnguard.device_management.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Sesión de streaming en vivo (HU-API-012, ADR-013 Propuesta).
 * MVP: en memoria, 1 sesión activa por device, TTL 5min, sin persistencia.
 * Fase 2: persistir en BD + tokens LiveKit reales.
 */
public record StreamSession(
        UUID sessionId,
        UUID deviceId,
        String room,
        String tokenViewer,
        UUID requestedBy,
        OffsetDateTime startedAt,
        OffsetDateTime expiresAt
) {
    public boolean isExpired(OffsetDateTime now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }
}
