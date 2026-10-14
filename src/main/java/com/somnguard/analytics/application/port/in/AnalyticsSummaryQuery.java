package com.somnguard.analytics.application.port.in;

import com.somnguard.analytics.adapter.in.web.dto.AnalyticsSummaryResponse;
import com.somnguard.analytics.application.usecase.AnalyticsFilter;
import java.util.UUID;

/**
 * Puerto de entrada: resumen de conducción (consolidado sin IA).
 * Implementado por {@code AnalyticsSummaryService}.
 */
public interface AnalyticsSummaryQuery {

    AnalyticsSummaryResponse summary(AnalyticsFilter filter, UUID scopeUserId, boolean adminView);
}
