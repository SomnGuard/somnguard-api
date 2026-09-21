package com.somnguard.telemetry_service.adapter.in.web.dto;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Proyección de lectura de un evento (HU-API-008 AC-003, FEA-TEL-QUERY).
 * Nombres legibles vía JOIN lógico con {@code event_type}, {@code severity}
 * y {@code media_type} (esta última vía {@code evidence} 1:1).
 */
public record EventDetailDto(
        UUID id,
        UUID deviceId,
        EventTypeRef eventType,
        SeverityRef severity,
        OffsetDateTime occurredAt,
        UUID soundPatternId,
        Map<String, Object> metadata,
        Boolean isOfflineSync,
        Boolean hasEvidence,
        MediaTypeRef mediaType,
        OffsetDateTime createdAt
) {
    public record EventTypeRef(UUID id, String code, String name) {}

    public record SeverityRef(UUID id, String code, String name, Short priority) {}

    public record MediaTypeRef(UUID id, String code, String name, String mimeType) {}
}
