package com.somnguard.analytics.application.usecase;

import com.somnguard.analytics.adapter.in.web.dto.AnalyticsMetricsResponse;
import com.somnguard.analytics.adapter.in.web.dto.AnalyticsReportResponse;
import com.somnguard.analytics.adapter.in.web.dto.AnalyticsSummaryResponse;
import com.somnguard.analytics.adapter.in.web.dto.AnalyticsTimelineResponse;
import com.somnguard.analytics.application.port.in.AnalyticsMetricsQuery;
import com.somnguard.analytics.application.port.in.AnalyticsReportQuery;
import com.somnguard.analytics.application.port.in.AnalyticsSummaryQuery;
import com.somnguard.analytics.application.port.in.AnalyticsTimelineQuery;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reportes convencionales: información consolidada vía API.
 * Compone métricas, resumen y un corte reciente de la línea de tiempo sin
 * duplicar lógica de negocio. Sin documentos, sin exportación, sin tareas
 * asíncronas y sin IA.
 */
@Service
public class AnalyticsReportService implements AnalyticsReportQuery {

    /** Eventos recientes incluidos en el reporte. */
    static final int RECENT_EVENTS_LIMIT = 10;

    private final AnalyticsMetricsQuery metricsQuery;
    private final AnalyticsSummaryQuery summaryQuery;
    private final AnalyticsTimelineQuery timelineQuery;

    public AnalyticsReportService(AnalyticsMetricsQuery metricsQuery,
            AnalyticsSummaryQuery summaryQuery,
            AnalyticsTimelineQuery timelineQuery) {
        this.metricsQuery = metricsQuery;
        this.summaryQuery = summaryQuery;
        this.timelineQuery = timelineQuery;
    }

    @Override
    @Transactional(readOnly = true)
    public AnalyticsReportResponse report(AnalyticsFilter filter, UUID scopeUserId,
            boolean adminView) {
        AnalyticsFilter safe = filter == null
                ? new AnalyticsFilter(null, null, null, null, null) : filter;
        AnalyticsMetricsResponse metrics = metricsQuery.metrics(safe, scopeUserId, adminView);
        AnalyticsSummaryResponse summary = summaryQuery.summary(safe, scopeUserId, adminView);
        AnalyticsTimelineResponse timeline =
                timelineQuery.timeline(safe, scopeUserId, adminView, 1, RECENT_EVENTS_LIMIT);
        return new AnalyticsReportResponse(OffsetDateTime.now(),
                new AnalyticsSummaryResponse.Range(safe.from(), safe.to()),
                new AnalyticsSummaryResponse.Scope(adminView,
                        summary.scope().deviceCount()),
                metrics, summary, timeline.data());
    }
}
