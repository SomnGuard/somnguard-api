package com.somnguard.analytics.adapter.in.web.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Reporte convencional: información consolidada vía API (sin documentos, sin
 * exportación, sin tareas asíncronas, sin IA). Reutiliza las respuestas de
 * métricas y resumen más un corte reciente de la línea de tiempo.
 */
public record AnalyticsReportResponse(
        OffsetDateTime generatedAt,
        AnalyticsSummaryResponse.Range range,
        AnalyticsSummaryResponse.Scope scope,
        AnalyticsMetricsResponse metrics,
        AnalyticsSummaryResponse summary,
        List<AnalyticsTimelineResponse.TimelineItemDto> recentEvents
) {
}
