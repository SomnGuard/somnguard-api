package com.somnguard.analytics.application.port.in;

import com.somnguard.analytics.adapter.in.web.dto.AnalyticsTimelineResponse;
import com.somnguard.analytics.application.usecase.AnalyticsFilter;
import java.util.UUID;

/**
 * Puerto de entrada: línea de tiempo de eventos de conducción.
 * Implementado por {@code AnalyticsTimelineService}.
 */
public interface AnalyticsTimelineQuery {

    AnalyticsTimelineResponse timeline(AnalyticsFilter filter, UUID scopeUserId,
            boolean adminView, int page, int pageSize);
}
