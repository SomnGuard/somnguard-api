package com.somnguard.analytics.application.port.in;

import com.somnguard.analytics.adapter.in.web.dto.AnalyticsReportResponse;
import com.somnguard.analytics.application.usecase.AnalyticsFilter;
import java.util.UUID;

/**
 * Puerto de entrada: reportes convencionales (información consolidada vía API,
 * sin documentos, sin exportación, sin IA). Implementado por
 * {@code AnalyticsReportService}.
 */
public interface AnalyticsReportQuery {

    AnalyticsReportResponse report(AnalyticsFilter filter, UUID scopeUserId, boolean adminView);
}
