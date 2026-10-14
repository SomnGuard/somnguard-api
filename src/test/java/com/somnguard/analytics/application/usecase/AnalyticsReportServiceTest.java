package com.somnguard.analytics.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.somnguard.analytics.adapter.in.web.dto.AnalyticsMetricsResponse;
import com.somnguard.analytics.adapter.in.web.dto.AnalyticsReportResponse;
import com.somnguard.analytics.adapter.in.web.dto.AnalyticsSummaryResponse;
import com.somnguard.analytics.adapter.in.web.dto.AnalyticsTimelineResponse;
import com.somnguard.analytics.application.port.in.AnalyticsMetricsQuery;
import com.somnguard.analytics.application.port.in.AnalyticsSummaryQuery;
import com.somnguard.analytics.application.port.in.AnalyticsTimelineQuery;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnalyticsReportServiceTest {

    @Mock
    AnalyticsMetricsQuery metricsQuery;
    @Mock
    AnalyticsSummaryQuery summaryQuery;
    @Mock
    AnalyticsTimelineQuery timelineQuery;

    AnalyticsReportService service;

    final UUID userId = UUID.randomUUID();
    final OffsetDateTime from = OffsetDateTime.parse("2026-03-01T00:00:00Z");
    final OffsetDateTime to = OffsetDateTime.parse("2026-03-08T00:00:00Z");

    @BeforeEach
    void setUp() {
        service = new AnalyticsReportService(metricsQuery, summaryQuery, timelineQuery);
    }

    @Test
    void reportComposesMetricsSummaryAndRecentEvents() {
        AnalyticsMetricsResponse metrics = new AnalyticsMetricsResponse(
                new AnalyticsMetricsResponse.Range(from, to),
                new AnalyticsMetricsResponse.Scope(false, 1),
                5L, List.of(), List.of(), List.of(), List.of(), null, null,
                new AnalyticsMetricsResponse.OfflineSync(0L, 5L, 0.0), null, "nota",
                new AnalyticsMetricsResponse.EventSpan(null, null, "nota"));
        AnalyticsSummaryResponse summary = new AnalyticsSummaryResponse(
                new AnalyticsSummaryResponse.Range(from, to),
                new AnalyticsSummaryResponse.Scope(false, 1),
                5L, List.of(), List.of(), null, null, null);
        AnalyticsTimelineResponse timeline = new AnalyticsTimelineResponse(List.of(),
                new AnalyticsTimelineResponse.Pagination(1, 10, 5L, 1));
        when(metricsQuery.metrics(any(AnalyticsFilter.class), eq(userId), eq(false)))
                .thenReturn(metrics);
        when(summaryQuery.summary(any(AnalyticsFilter.class), eq(userId), eq(false)))
                .thenReturn(summary);
        when(timelineQuery.timeline(any(AnalyticsFilter.class), eq(userId), eq(false),
                eq(1), eq(10))).thenReturn(timeline);
        AnalyticsReportResponse response = service.report(
                new AnalyticsFilter(null, null, null, from, to), userId, false);
        assertNotNull(response.generatedAt());
        assertEquals(5L, response.metrics().totalEvents());
        assertEquals(5L, response.summary().totalEvents());
        assertTrue(response.recentEvents().isEmpty());
        assertEquals(from, response.range().from());
        verify(timelineQuery).timeline(any(AnalyticsFilter.class), eq(userId), eq(false),
                eq(1), eq(10));
    }

    @Test
    void reportPropagatesInvalidRangeFromMetrics() {
        when(metricsQuery.metrics(any(AnalyticsFilter.class), any(), anyBoolean()))
                .thenThrow(new IllegalArgumentException("from debe ser anterior o igual a to"));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.report(new AnalyticsFilter(null, null, null, to, from),
                        userId, false));
        assertEquals("from debe ser anterior o igual a to", ex.getMessage());
    }
}
