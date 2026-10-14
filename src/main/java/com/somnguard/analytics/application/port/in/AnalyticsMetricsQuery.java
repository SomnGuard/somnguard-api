package com.somnguard.analytics.application.port.in;

import com.somnguard.analytics.adapter.in.web.dto.AnalyticsMetricsResponse;
import com.somnguard.analytics.application.usecase.AnalyticsFilter;
import java.util.UUID;

/**
 * Puerto de entrada: métricas de frecuencia y severidad.
 * Implementado por {@code AnalyticsMetricsService}.
 */
public interface AnalyticsMetricsQuery {

    AnalyticsMetricsResponse metrics(AnalyticsFilter filter, UUID scopeUserId, boolean adminView);
}
