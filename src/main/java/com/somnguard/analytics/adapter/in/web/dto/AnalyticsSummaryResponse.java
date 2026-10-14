package com.somnguard.analytics.adapter.in.web.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Resumen de conducción: consolidado sin IA para portal y app.
 * Con cero eventos los totales quedan en cero, las listas vacías y los
 * extremos en {@code null}; la tendencia queda en {@code null} cuando el
 * periodo no está acotado por ambos extremos.
 */
public record AnalyticsSummaryResponse(
        Range range,
        Scope scope,
        long totalEvents,
        List<SeveritySlice> bySeverity,
        List<TopType> topTypes,
        OffsetDateTime firstEventAt,
        OffsetDateTime lastEventAt,
        Trend trend
) {
    public record Range(OffsetDateTime from, OffsetDateTime to) {
    }

    public record Scope(boolean adminView, int deviceCount) {
    }

    public record SeveritySlice(UUID severityId, String code, String name,
            short priority, long count) {
    }

    public record TopType(UUID eventTypeId, String code, String name, long count) {
    }

    public record Trend(long previousTotal, long currentTotal, long delta, Double percentChange) {
    }
}
