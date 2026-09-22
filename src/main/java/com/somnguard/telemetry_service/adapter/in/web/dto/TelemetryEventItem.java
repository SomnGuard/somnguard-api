package com.somnguard.telemetry_service.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Un evento del lote HU-API-007 AC-001/AC-002.
 * Contrato edge ({@code app/common/models.py::to_telemetry_dict}): solo metadata JSON,
 * códigos de catálogo (no ids), {@code sound_pattern} opcional con fallback server.
 */
public record TelemetryEventItem(
        @NotNull UUID event_id,
        @NotNull UUID device_id,
        @NotNull OffsetDateTime occurred_at,
        @NotNull String event_type,
        @NotNull String severity,
        String sound_pattern,
        Map<String, Object> metadata,
        Boolean is_offline_sync,
        Boolean has_evidence) {}
