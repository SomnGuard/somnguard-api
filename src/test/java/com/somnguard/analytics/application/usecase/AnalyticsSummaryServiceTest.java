package com.somnguard.analytics.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.somnguard.analytics.adapter.in.web.dto.AnalyticsMetricsResponse;
import com.somnguard.analytics.adapter.in.web.dto.AnalyticsSummaryResponse;
import com.somnguard.analytics.application.port.in.AnalyticsMetricsQuery;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnalyticsSummaryServiceTest {

    @Mock
    AnalyticsMetricsQuery metricsQuery;

    AnalyticsSummaryService service;

    final UUID userId = UUID.randomUUID();
    final OffsetDateTime from = OffsetDateTime.parse("2026-03-08T00:00:00Z");
    final OffsetDateTime to = OffsetDateTime.parse("2026-03-15T00:00:00Z");

    @BeforeEach
    void setUp() {
        service = new AnalyticsSummaryService(metricsQuery);
    }

    private AnalyticsMetricsResponse metrics(long total) {
        return new AnalyticsMetricsResponse(
                new AnalyticsMetricsResponse.Range(from, to),
                new AnalyticsMetricsResponse.Scope(false, 1),
                total,
                List.of(new AnalyticsMetricsResponse.ByType(UUID.randomUUID(), "EV-SOM-01",
                        "Bostezo", total)),
                List.of(new AnalyticsMetricsResponse.BySeverity(UUID.randomUUID(), "warning",
                        "Moderada", (short) 2, total)),
                List.of(), List.of(),
                OffsetDateTime.parse("2026-03-09T08:00:00Z"),
                OffsetDateTime.parse("2026-03-14T18:00:00Z"),
                new AnalyticsMetricsResponse.OfflineSync(0L, total, 0.0),
                2.0, "nota",
                new AnalyticsMetricsResponse.EventSpan(
                        OffsetDateTime.parse("2026-03-09T08:00:00Z"),
                        OffsetDateTime.parse("2026-03-14T18:00:00Z"), "nota"));
    }

    @Test
    void summaryReusesMetricsAndComputesTrend() {
        when(metricsQuery.metrics(any(AnalyticsFilter.class), eq(userId), eq(false)))
                .thenReturn(metrics(150L), metrics(100L));
        AnalyticsSummaryResponse response = service.summary(
                new AnalyticsFilter(null, null, null, from, to), userId, false);
        assertEquals(150L, response.totalEvents());
        assertEquals(1, response.topTypes().size());
        assertEquals(1, response.bySeverity().size());
        assertNotNull(response.trend());
        assertEquals(100L, response.trend().previousTotal());
        assertEquals(150L, response.trend().currentTotal());
        assertEquals(50L, response.trend().delta());
        assertEquals(50.0, response.trend().percentChange());
    }

    @Test
    void summaryLeavesTrendNullWithoutClosedRange() {
        when(metricsQuery.metrics(any(AnalyticsFilter.class), eq(userId), eq(false)))
                .thenReturn(metrics(10L));
        AnalyticsSummaryResponse response = service.summary(
                new AnalyticsFilter(null, null, null, null, to), userId, false);
        assertEquals(10L, response.totalEvents());
        assertNull(response.trend());
    }

    @Test
    void summaryKeepsEmptyContract() {
        AnalyticsMetricsResponse empty = new AnalyticsMetricsResponse(
                new AnalyticsMetricsResponse.Range(from, to),
                new AnalyticsMetricsResponse.Scope(false, 0),
                0L, List.of(), List.of(), List.of(), List.of(), null, null,
                new AnalyticsMetricsResponse.OfflineSync(0L, 0L, 0.0), null, "nota",
                new AnalyticsMetricsResponse.EventSpan(null, null, "nota"));
        when(metricsQuery.metrics(any(AnalyticsFilter.class), eq(userId), eq(false)))
                .thenReturn(empty, empty);
        AnalyticsSummaryResponse response = service.summary(
                new AnalyticsFilter(null, null, null, from, to), userId, false);
        assertEquals(0L, response.totalEvents());
        assertTrue(response.topTypes().isEmpty());
        assertNull(response.firstEventAt());
        assertNotNull(response.trend());
        assertEquals(0L, response.trend().previousTotal());
        assertNull(response.trend().percentChange());
    }
}
