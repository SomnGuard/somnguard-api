package com.somnguard.analytics.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import com.somnguard.analytics.adapter.in.web.dto.AnalyticsMetricsResponse;
import com.somnguard.analytics.application.port.out.AnalyticsEventQueryPort;
import com.somnguard.telemetry_service.domain.exception.InvalidCatalogReferenceException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnalyticsMetricsServiceTest {

    @Mock
    AnalyticsEventQueryPort queries;

    AnalyticsMetricsService service;

    final UUID userId = UUID.randomUUID();
    final UUID deviceId = UUID.randomUUID();
    final UUID typeA = UUID.randomUUID();
    final UUID typeB = UUID.randomUUID();
    final UUID sevWarn = UUID.randomUUID();
    final UUID sevCrit = UUID.randomUUID();
    final UUID catSom = UUID.randomUUID();

    final OffsetDateTime from = OffsetDateTime.parse("2026-03-01T00:00:00Z");
    final OffsetDateTime to = OffsetDateTime.parse("2026-03-03T00:00:00Z");
    final OffsetDateTime first = OffsetDateTime.parse("2026-03-01T08:00:00Z");
    final OffsetDateTime last = OffsetDateTime.parse("2026-03-02T18:00:00Z");

    @BeforeEach
    void setUp() {
        service = new AnalyticsMetricsService(queries);
    }

    private void stubFullPeriod() {
        when(queries.countEvents(isNull(), isNull(), isNull(), any(), any(), isNull()))
                .thenReturn(10L);
        when(queries.countDistinctDevices(isNull(), isNull(), isNull(), any(), any(), isNull()))
                .thenReturn(2L);
        when(queries.countByType(isNull(), isNull(), isNull(), any(), any(), isNull()))
                .thenReturn(List.of(
                        new AnalyticsEventQueryPort.TypeCount(typeA, 7L),
                        new AnalyticsEventQueryPort.TypeCount(typeB, 3L)));
        when(queries.countBySeverity(isNull(), isNull(), isNull(), any(), any(), isNull()))
                .thenReturn(List.of(
                        new AnalyticsEventQueryPort.SeverityCount(sevWarn, 6L),
                        new AnalyticsEventQueryPort.SeverityCount(sevCrit, 4L)));
        when(queries.findTypeViews(any(Set.class))).thenReturn(Map.of(
                typeA, new AnalyticsEventQueryPort.TypeView(typeA, "EV-SOM-01",
                        "Bostezo", catSom),
                typeB, new AnalyticsEventQueryPort.TypeView(typeB, "EV-DIS-01",
                        "Distracción", catSom)));
        when(queries.findSeverityViews(any(Set.class))).thenReturn(Map.of(
                sevWarn, new AnalyticsEventQueryPort.SeverityView(sevWarn, "warning",
                        "Moderada", (short) 2),
                sevCrit, new AnalyticsEventQueryPort.SeverityView(sevCrit, "critical",
                        "Crítica", (short) 4)));
        when(queries.findCategoryViews(any(Set.class))).thenReturn(Map.of(
                catSom, new AnalyticsEventQueryPort.CategoryView(catSom, "SOM", "Somnolencia")));
        when(queries.countOfflineSync(isNull(), isNull(), isNull(), any(), any(), isNull()))
                .thenReturn(2L);
        when(queries.findEarliestOccurredAt(isNull(), isNull(), isNull(), any(), any(), isNull()))
                .thenReturn(java.util.Optional.of(first));
        when(queries.findLatestOccurredAt(isNull(), isNull(), isNull(), any(), any(), isNull()))
                .thenReturn(java.util.Optional.of(last));
        when(queries.findOccurredAts(isNull(), isNull(), isNull(), any(), any(), isNull()))
                .thenReturn(List.of(
                        first, first.plusHours(1), first.plusHours(2),
                        last.minusHours(2), last.minusHours(1), last,
                        last, last, last, last));
    }

    @Test
    void metricsAggregatesDistributions() {
        stubFullPeriod();
        AnalyticsMetricsResponse response =
                service.metrics(new AnalyticsFilter(null, null, null, from, to), null, true);
        assertEquals(10L, response.totalEvents());
        assertEquals(2, response.byType().size());
        assertEquals(typeA, response.byType().get(0).eventTypeId());
        assertEquals(7L, response.byType().get(0).count());
        assertEquals(2, response.bySeverity().size());
        assertEquals(1, response.byCategory().size());
        assertEquals("SOM", response.byCategory().get(0).code());
        assertEquals(10L, response.byCategory().get(0).count());
        assertEquals(first, response.firstEventAt());
        assertEquals(last, response.lastEventAt());
        assertEquals(2L, response.offlineSync().offlineCount());
        assertEquals(0.2, response.offlineSync().ratio());
        // (6*2 + 4*4) / 10 = 2.8
        assertEquals(2.8, response.severityPriorityAvg());
        assertNotNull(response.severityPriorityNote());
        assertNotNull(response.eventSpan().note());
        assertEquals(2, response.scope().deviceCount());
    }

    @Test
    void metricsFillsMissingDaysWithZero() {
        stubFullPeriod();
        AnalyticsMetricsResponse response =
                service.metrics(new AnalyticsFilter(null, null, null, from, to), null, true);
        // 2026-03-01: 3 eventos, 2026-03-02: 7 eventos, 2026-03-03: 0 (relleno)
        assertEquals(3, response.byDay().size());
        assertEquals(3L, response.byDay().get(0).count());
        assertEquals(7L, response.byDay().get(1).count());
        assertEquals(0L, response.byDay().get(2).count());
    }

    @Test
    void metricsReturnsEmptyContractWithoutEvents() {
        when(queries.countEvents(isNull(), isNull(), isNull(), any(), any(), isNull()))
                .thenReturn(0L);
        when(queries.countDistinctDevices(isNull(), isNull(), isNull(), any(), any(), isNull()))
                .thenReturn(0L);
        AnalyticsMetricsResponse response =
                service.metrics(new AnalyticsFilter(null, null, null, from, to), null, true);
        assertEquals(0L, response.totalEvents());
        assertTrue(response.byType().isEmpty());
        assertTrue(response.bySeverity().isEmpty());
        assertTrue(response.byCategory().isEmpty());
        assertTrue(response.byDay().isEmpty());
        assertNull(response.firstEventAt());
        assertNull(response.lastEventAt());
        assertNull(response.severityPriorityAvg());
    }

    @Test
    void metricsReturnsEmptyWhenUserOwnsNothing() {
        when(queries.findOwnedDeviceIds(userId)).thenReturn(List.of());
        AnalyticsMetricsResponse response = service.metrics(
                new AnalyticsFilter(null, null, null, from, to), userId, false);
        assertEquals(0L, response.totalEvents());
    }

    @Test
    void metricsRejectsInvertedRange() {
        AnalyticsFilter filter = new AnalyticsFilter(null, null, null, to, from);
        assertThrows(IllegalArgumentException.class,
                () -> service.metrics(filter, null, true));
    }

    @Test
    void metricsRejectsRangeOver93Days() {
        AnalyticsFilter filter = new AnalyticsFilter(null, null, null, from,
                from.plusDays(94));
        assertThrows(IllegalArgumentException.class,
                () -> service.metrics(filter, null, true));
    }

    @Test
    void metricsPropagatesUnknownSeverity() {
        when(queries.resolveSeverityId("DESCONOCIDA"))
                .thenThrow(new InvalidCatalogReferenceException("severity desconocida"));
        assertThrows(InvalidCatalogReferenceException.class, () -> service.metrics(
                new AnalyticsFilter(null, null, "DESCONOCIDA", from, to), null, true));
    }
}
