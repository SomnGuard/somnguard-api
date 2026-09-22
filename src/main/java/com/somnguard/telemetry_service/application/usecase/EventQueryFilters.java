package com.somnguard.telemetry_service.application.usecase;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Filtros HU-API-008 AC-001 (FEA-TEL-QUERY).
 * {@code severity} acepta UUID de catálogo o código
 * ({@code critical}, {@code CRITICA}, {@code INFO}, ...).
 */
public record EventQueryFilters(
        UUID deviceId,
        UUID eventTypeId,
        String severity,
        OffsetDateTime from,
        OffsetDateTime to
) {}
