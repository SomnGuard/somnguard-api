package com.somnguard.analytics.adapter.in.web.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Métricas de frecuencia y severidad calculadas con agregaciones en base de
 * datos. Todas las distribuciones derivan de datos reales; con cero eventos
 * las listas quedan vacías y los extremos en {@code null}.
 */
public record AnalyticsMetricsResponse(
        Range range,
        Scope scope,
        long totalEvents,
        List<ByType> byType,
        List<BySeverity> bySeverity,
        List<ByCategory> byCategory,
        List<ByDay> byDay,
        OffsetDateTime firstEventAt,
        OffsetDateTime lastEventAt,
        OfflineSync offlineSync,
        Double severityPriorityAvg,
        String severityPriorityNote,
        EventSpan eventSpan
) {
    public record Range(OffsetDateTime from, OffsetDateTime to) {
    }

    public record Scope(boolean adminView, int deviceCount) {
    }

    public record ByType(UUID eventTypeId, String code, String name, long count) {
    }

    public record BySeverity(UUID severityId, String code, String name, short priority, long count) {
    }

    public record ByCategory(UUID eventCategoryId, String code, String name, long count) {
    }

    public record ByDay(LocalDate date, long count) {
    }

    public record OfflineSync(long offlineCount, long total, double ratio) {
    }

    public record EventSpan(OffsetDateTime firstEventAt, OffsetDateTime lastEventAt, String note) {
    }
}
