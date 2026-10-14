package com.somnguard.analytics.application.usecase;

import com.somnguard.analytics.adapter.in.web.dto.AnalyticsMetricsResponse;
import com.somnguard.analytics.adapter.in.web.dto.AnalyticsSummaryResponse;
import com.somnguard.analytics.application.port.in.AnalyticsMetricsQuery;
import com.somnguard.analytics.application.port.in.AnalyticsSummaryQuery;
import com.somnguard.analytics.domain.service.AnalyticsTrendCalculator;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resumen de conducción.
 * Reutiliza {@link AnalyticsMetricsQuery} sin duplicar agregaciones y añade la
 * tendencia frente al periodo anterior comparable (misma duración,
 * inmediatamente anterior). Sin IA y sin indicadores inventados.
 */
@Service
public class AnalyticsSummaryService implements AnalyticsSummaryQuery {

    /** Tipos más frecuentes incluidos en el resumen. */
    static final int TOP_TYPES_LIMIT = 5;

    private final AnalyticsMetricsQuery metricsQuery;

    public AnalyticsSummaryService(AnalyticsMetricsQuery metricsQuery) {
        this.metricsQuery = metricsQuery;
    }

    @Override
    @Transactional(readOnly = true)
    public AnalyticsSummaryResponse summary(AnalyticsFilter filter, UUID scopeUserId,
            boolean adminView) {
        AnalyticsMetricsResponse metrics = metricsQuery.metrics(filter, scopeUserId, adminView);
        AnalyticsFilter safe = filter == null
                ? new AnalyticsFilter(null, null, null, null, null) : filter;
        return new AnalyticsSummaryResponse(
                new AnalyticsSummaryResponse.Range(safe.from(), safe.to()),
                new AnalyticsSummaryResponse.Scope(metrics.scope().adminView(),
                        metrics.scope().deviceCount()),
                metrics.totalEvents(),
                toSeveritySlices(metrics),
                toTopTypes(metrics),
                metrics.firstEventAt(),
                metrics.lastEventAt(),
                toTrend(safe, scopeUserId, adminView, metrics.totalEvents()));
    }

    private List<AnalyticsSummaryResponse.SeveritySlice> toSeveritySlices(
            AnalyticsMetricsResponse metrics) {
        return metrics.bySeverity().stream()
                .map(slice -> new AnalyticsSummaryResponse.SeveritySlice(
                        slice.severityId(), slice.code(), slice.name(),
                        slice.priority(), slice.count()))
                .toList();
    }

    private List<AnalyticsSummaryResponse.TopType> toTopTypes(AnalyticsMetricsResponse metrics) {
        return metrics.byType().stream().limit(TOP_TYPES_LIMIT)
                .map(type -> new AnalyticsSummaryResponse.TopType(
                        type.eventTypeId(), type.code(), type.name(), type.count()))
                .toList();
    }

    /**
     * Tendencia frente al periodo anterior de igual duración.
     * Solo se calcula con rango acotado por ambos extremos; en otro caso
     * queda en {@code null} (contrato explícito, sin datos ficticios).
     */
    private AnalyticsSummaryResponse.Trend toTrend(AnalyticsFilter safe, UUID scopeUserId,
            boolean adminView, long currentTotal) {
        if (safe.from() == null || safe.to() == null) {
            return null;
        }
        Duration duration = Duration.between(safe.from(), safe.to());
        AnalyticsFilter previous = new AnalyticsFilter(safe.deviceId(), safe.eventTypeId(),
                safe.severity(), safe.from().minus(duration), safe.from());
        long previousTotal;
        try {
            previousTotal = metricsQuery.metrics(previous, scopeUserId, adminView).totalEvents();
        } catch (IllegalArgumentException ex) {
            return null;
        }
        AnalyticsTrendCalculator.Trend trend =
                AnalyticsTrendCalculator.trend(previousTotal, currentTotal);
        return new AnalyticsSummaryResponse.Trend(trend.previousTotal(),
                trend.currentTotal(), trend.delta(), trend.percentChange());
    }
}
