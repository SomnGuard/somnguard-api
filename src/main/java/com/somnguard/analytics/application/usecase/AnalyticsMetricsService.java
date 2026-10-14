package com.somnguard.analytics.application.usecase;

import com.somnguard.analytics.adapter.in.web.dto.AnalyticsMetricsResponse;
import com.somnguard.analytics.application.port.in.AnalyticsMetricsQuery;
import com.somnguard.analytics.application.port.out.AnalyticsEventQueryPort;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Métricas de frecuencia y severidad con agregaciones en base de datos.
 * Sin puntuaciones inventadas ni horas de operación: el intervalo temporal se
 * expone como vano entre eventos con nota explícita.
 */
@Service
public class AnalyticsMetricsService implements AnalyticsMetricsQuery {

    static final String SEVERITY_PRIORITY_NOTE = "Promedio ponderado de priority del catálogo "
            + "severity (ordinal 1..N; a mayor valor, mayor criticidad). "
            + "No es una puntuación de riesgo.";

    static final String EVENT_SPAN_NOTE = "Intervalo entre el primer y el último evento "
            + "del periodo. No representa horas efectivas de conducción.";

    /** Tope del vano de datos para la evolución diaria cuando el rango es abierto. */
    static final long MAX_DATA_SPAN_DAYS = 366L;

    private final AnalyticsEventQueryPort queries;

    public AnalyticsMetricsService(AnalyticsEventQueryPort queries) {
        this.queries = queries;
    }

    @Override
    @Transactional(readOnly = true)
    public AnalyticsMetricsResponse metrics(AnalyticsFilter filter, UUID scopeUserId,
            boolean adminView) {
        AnalyticsFilter safe = filter == null
                ? new AnalyticsFilter(null, null, null, null, null) : filter;
        AnalyticsValidation.validateMaxRange(safe.from(), safe.to());
        UUID severityId = queries.resolveSeverityId(safe.severity());
        List<UUID> scopeIds = resolveScopeIds(safe.deviceId(), scopeUserId, adminView);
        if (scopeIds != null && scopeIds.isEmpty()) {
            return emptyMetrics(safe, adminView, 0);
        }
        long total = queries.countEvents(safe.deviceId(), safe.eventTypeId(), severityId,
                safe.from(), safe.to(), scopeIds);
        if (total == 0) {
            return emptyMetrics(safe, adminView, deviceCount(safe, severityId, scopeIds));
        }
        return buildMetrics(safe, severityId, scopeIds, adminView, total);
    }

    List<UUID> resolveScopeIds(UUID deviceId, UUID scopeUserId, boolean adminView) {
        if (adminView) {
            return null;
        }
        List<UUID> owned = queries.findOwnedDeviceIds(scopeUserId);
        if (deviceId != null) {
            return owned.contains(deviceId) ? null : List.of();
        }
        return owned;
    }

    private long deviceCount(AnalyticsFilter safe, UUID severityId, List<UUID> scopeIds) {
        return queries.countDistinctDevices(safe.deviceId(), safe.eventTypeId(), severityId,
                safe.from(), safe.to(), scopeIds);
    }

    private AnalyticsMetricsResponse buildMetrics(AnalyticsFilter safe, UUID severityId,
            List<UUID> scopeIds, boolean adminView, long total) {
        List<AnalyticsEventQueryPort.TypeCount> typeCounts = queries.countByType(
                safe.deviceId(), safe.eventTypeId(), severityId,
                safe.from(), safe.to(), scopeIds);
        List<AnalyticsEventQueryPort.SeverityCount> severityCounts = queries.countBySeverity(
                safe.deviceId(), safe.eventTypeId(), severityId,
                safe.from(), safe.to(), scopeIds);
        Map<UUID, AnalyticsEventQueryPort.TypeView> types = queries.findTypeViews(
                typeCounts.stream().map(AnalyticsEventQueryPort.TypeCount::eventTypeId)
                        .collect(Collectors.toSet()));
        Map<UUID, AnalyticsEventQueryPort.SeverityView> severities = queries.findSeverityViews(
                severityCounts.stream().map(AnalyticsEventQueryPort.SeverityCount::severityId)
                        .collect(Collectors.toSet()));
        Map<UUID, AnalyticsEventQueryPort.CategoryView> categories = queries.findCategoryViews(
                types.values().stream().map(AnalyticsEventQueryPort.TypeView::eventCategoryId)
                        .collect(Collectors.toSet()));
        long offline = queries.countOfflineSync(safe.deviceId(), safe.eventTypeId(), severityId,
                safe.from(), safe.to(), scopeIds);
        OffsetDateTime first = queries.findEarliestOccurredAt(safe.deviceId(), safe.eventTypeId(),
                severityId, safe.from(), safe.to(), scopeIds).orElse(null);
        OffsetDateTime last = queries.findLatestOccurredAt(safe.deviceId(), safe.eventTypeId(),
                severityId, safe.from(), safe.to(), scopeIds).orElse(null);
        return new AnalyticsMetricsResponse(
                new AnalyticsMetricsResponse.Range(safe.from(), safe.to()),
                new AnalyticsMetricsResponse.Scope(adminView,
                        (int) deviceCount(safe, severityId, scopeIds)),
                total,
                toByType(typeCounts, types),
                toBySeverity(severityCounts, severities),
                toByCategory(typeCounts, types, categories),
                toByDay(safe, severityId, scopeIds, first, last),
                first, last,
                new AnalyticsMetricsResponse.OfflineSync(offline, total,
                        total == 0 ? 0.0 : Math.round((offline * 1000.0) / total) / 1000.0),
                severityPriorityAvg(severityCounts, severities),
                SEVERITY_PRIORITY_NOTE,
                new AnalyticsMetricsResponse.EventSpan(first, last, EVENT_SPAN_NOTE));
    }

    private List<AnalyticsMetricsResponse.ByType> toByType(
            List<AnalyticsEventQueryPort.TypeCount> counts,
            Map<UUID, AnalyticsEventQueryPort.TypeView> types) {
        return counts.stream()
                .sorted(Comparator.comparingLong(AnalyticsEventQueryPort.TypeCount::count).reversed())
                .map(count -> {
                    AnalyticsEventQueryPort.TypeView type = types.get(count.eventTypeId());
                    return new AnalyticsMetricsResponse.ByType(count.eventTypeId(),
                            type == null ? null : type.code(),
                            type == null ? null : type.name(), count.count());
                }).toList();
    }

    private List<AnalyticsMetricsResponse.BySeverity> toBySeverity(
            List<AnalyticsEventQueryPort.SeverityCount> counts,
            Map<UUID, AnalyticsEventQueryPort.SeverityView> severities) {
        return counts.stream()
                .sorted(Comparator.comparingLong(
                        AnalyticsEventQueryPort.SeverityCount::count).reversed())
                .map(count -> {
                    AnalyticsEventQueryPort.SeverityView severity =
                            severities.get(count.severityId());
                    return new AnalyticsMetricsResponse.BySeverity(count.severityId(),
                            severity == null ? null : severity.code(),
                            severity == null ? null : severity.name(),
                            severity == null ? 0 : severity.priority(), count.count());
                }).toList();
    }

    private List<AnalyticsMetricsResponse.ByCategory> toByCategory(
            List<AnalyticsEventQueryPort.TypeCount> counts,
            Map<UUID, AnalyticsEventQueryPort.TypeView> types,
            Map<UUID, AnalyticsEventQueryPort.CategoryView> categories) {
        Map<UUID, Long> totals = new LinkedHashMap<>();
        for (AnalyticsEventQueryPort.TypeCount count : counts) {
            AnalyticsEventQueryPort.TypeView type = types.get(count.eventTypeId());
            UUID categoryId = type == null ? null : type.eventCategoryId();
            if (categoryId != null) {
                totals.merge(categoryId, count.count(), Long::sum);
            }
        }
        return totals.entrySet().stream()
                .sorted(Map.Entry.<UUID, Long>comparingByValue().reversed())
                .map(entry -> {
                    AnalyticsEventQueryPort.CategoryView category =
                            categories.get(entry.getKey());
                    return new AnalyticsMetricsResponse.ByCategory(entry.getKey(),
                            category == null ? null : category.code(),
                            category == null ? null : category.name(), entry.getValue());
                }).toList();
    }

    private Double severityPriorityAvg(List<AnalyticsEventQueryPort.SeverityCount> counts,
            Map<UUID, AnalyticsEventQueryPort.SeverityView> severities) {
        long total = 0L;
        long weighted = 0L;
        for (AnalyticsEventQueryPort.SeverityCount count : counts) {
            AnalyticsEventQueryPort.SeverityView severity = severities.get(count.severityId());
            short priority = severity == null ? 0 : severity.priority();
            total += count.count();
            weighted += (long) priority * count.count();
        }
        if (total == 0) {
            return null;
        }
        return Math.round((weighted * 100.0) / total) / 100.0;
    }

    private List<AnalyticsMetricsResponse.ByDay> toByDay(AnalyticsFilter safe, UUID severityId,
            List<UUID> scopeIds, OffsetDateTime first, OffsetDateTime last) {
        if (first == null || last == null) {
            return List.of();
        }
        LocalDate start = safe.from() != null ? safe.from().toLocalDate() : first.toLocalDate();
        LocalDate end = safe.to() != null ? safe.to().toLocalDate() : last.toLocalDate();
        if (start.isAfter(end)) {
            return List.of();
        }
        if (java.time.temporal.ChronoUnit.DAYS.between(start, end) > MAX_DATA_SPAN_DAYS) {
            throw new IllegalArgumentException(
                    "rango de datos excede " + MAX_DATA_SPAN_DAYS + " días; acote from/to");
        }
        Map<LocalDate, Long> totals = new LinkedHashMap<>();
        for (OffsetDateTime occurredAt : queries.findOccurredAts(safe.deviceId(),
                safe.eventTypeId(), severityId, safe.from(), safe.to(), scopeIds)) {
            if (occurredAt != null) {
                totals.merge(occurredAt.toLocalDate(), 1L, Long::sum);
            }
        }
        List<AnalyticsMetricsResponse.ByDay> days = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            days.add(new AnalyticsMetricsResponse.ByDay(date, totals.getOrDefault(date, 0L)));
        }
        return days;
    }

    private AnalyticsMetricsResponse emptyMetrics(AnalyticsFilter safe, boolean adminView,
            long devices) {
        return new AnalyticsMetricsResponse(
                new AnalyticsMetricsResponse.Range(safe.from(), safe.to()),
                new AnalyticsMetricsResponse.Scope(adminView, (int) devices),
                0L, List.of(), List.of(), List.of(), List.of(), null, null,
                new AnalyticsMetricsResponse.OfflineSync(0L, 0L, 0.0),
                null, SEVERITY_PRIORITY_NOTE,
                new AnalyticsMetricsResponse.EventSpan(null, null, EVENT_SPAN_NOTE));
    }
}
