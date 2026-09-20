package com.somnguard.telemetry_service.adapter.in.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Lote HU-API-007 AC-001/AC-007: máx 100 eventos, solo metadata JSON (sin archivos inline).
 */
public record TelemetryBatchRequest(
        @NotNull @Size(min = 1, max = 100) List<@Valid TelemetryEventItem> events) {}
