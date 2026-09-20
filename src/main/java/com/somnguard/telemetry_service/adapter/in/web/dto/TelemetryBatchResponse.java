package com.somnguard.telemetry_service.adapter.in.web.dto;

import java.util.List;
import java.util.UUID;

/**
 * ACK HU-API-007 AC-006: el device borra del buffer local ambos (acked + duplicate).
 * Jackson usa SNAKE_CASE global ({@code application.yml}), así que serializa
 * {@code acked_ids}/{@code duplicate_ids} como espera el edge.
 */
public record TelemetryBatchResponse(List<UUID> ackedIds, List<UUID> duplicateIds) {}
