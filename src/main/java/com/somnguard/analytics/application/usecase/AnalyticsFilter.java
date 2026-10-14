package com.somnguard.analytics.application.usecase;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Filtros compartidos de Analytics (línea de tiempo, métricas, resumen y reportes).
 * Subconjunto compatible con {@code EventQueryFilters}: {@code severity} acepta
 * UUID de catálogo o código ({@code critical}, {@code CRITICA}, {@code INFO}, ...).
 *
 * <p>No se añaden {@code event_category_id} ni {@code has_evidence} como filtros:
 * la distribución por categoría se calcula en la respuesta de métricas y el
 * filtrado extra no se justifica para esta fase.
 */
public record AnalyticsFilter(
        UUID deviceId,
        UUID eventTypeId,
        String severity,
        OffsetDateTime from,
        OffsetDateTime to
) {
}
