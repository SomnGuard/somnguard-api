package com.somnguard.analytics.application.usecase;

import com.somnguard.analytics.adapter.in.web.dto.AnalyticsTimelineResponse;
import com.somnguard.analytics.application.port.in.AnalyticsTimelineQuery;
import com.somnguard.analytics.application.port.out.AnalyticsEventQueryPort;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Línea de tiempo de eventos de conducción.
 * Solo lectura: respeta el alcance autorizado, reutiliza la paginación
 * canónica ({@code occurredAt DESC, id ASC}) y enriquece por lotes (sin N+1).
 */
@Service
public class AnalyticsTimelineService implements AnalyticsTimelineQuery {

    private final AnalyticsEventQueryPort queries;

    public AnalyticsTimelineService(AnalyticsEventQueryPort queries) {
        this.queries = queries;
    }

    @Override
    @Transactional(readOnly = true)
    public AnalyticsTimelineResponse timeline(AnalyticsFilter filter, UUID scopeUserId,
            boolean adminView, int page, int pageSize) {
        AnalyticsFilter safe = filter == null
                ? new AnalyticsFilter(null, null, null, null, null) : filter;
        AnalyticsValidation.validateRange(safe.from(), safe.to());
        int safePage = AnalyticsValidation.safePage(page);
        int safeSize = AnalyticsValidation.safeSize(pageSize);
        UUID severityId = queries.resolveSeverityId(safe.severity());
        List<UUID> scopeIds = resolveScopeIds(safe.deviceId(), scopeUserId, adminView);
        if (scopeIds != null && scopeIds.isEmpty()) {
            return emptyResponse(safePage, safeSize);
        }
        Pageable pageable = PageRequest.of(safePage - 1, safeSize,
                Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.asc("id")));
        Page<AnalyticsEventQueryPort.TimelineRow> result = queries.findTimeline(
                safe.deviceId(), safe.eventTypeId(), severityId,
                safe.from(), safe.to(), scopeIds, pageable);
        return new AnalyticsTimelineResponse(toItems(result.getContent()),
                new AnalyticsTimelineResponse.Pagination(safePage, safeSize,
                        result.getTotalElements(), result.getTotalPages()));
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

    private AnalyticsTimelineResponse emptyResponse(int safePage, int safeSize) {
        return new AnalyticsTimelineResponse(List.of(),
                new AnalyticsTimelineResponse.Pagination(safePage, safeSize, 0, 0));
    }

    private List<AnalyticsTimelineResponse.TimelineItemDto> toItems(
            List<AnalyticsEventQueryPort.TimelineRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<UUID, AnalyticsEventQueryPort.TypeView> types = queries.findTypeViews(
                rows.stream().map(AnalyticsEventQueryPort.TimelineRow::eventTypeId)
                        .collect(Collectors.toSet()));
        Map<UUID, AnalyticsEventQueryPort.SeverityView> severities = queries.findSeverityViews(
                rows.stream().map(AnalyticsEventQueryPort.TimelineRow::severityId)
                        .collect(Collectors.toSet()));
        Map<UUID, AnalyticsEventQueryPort.CategoryView> categories = queries.findCategoryViews(
                types.values().stream()
                        .map(AnalyticsEventQueryPort.TypeView::eventCategoryId)
                        .collect(Collectors.toSet()));
        List<AnalyticsTimelineResponse.TimelineItemDto> data = new ArrayList<>(rows.size());
        for (AnalyticsEventQueryPort.TimelineRow row : rows) {
            data.add(toItem(row, types, severities, categories));
        }
        return data;
    }

    private AnalyticsTimelineResponse.TimelineItemDto toItem(
            AnalyticsEventQueryPort.TimelineRow row,
            Map<UUID, AnalyticsEventQueryPort.TypeView> types,
            Map<UUID, AnalyticsEventQueryPort.SeverityView> severities,
            Map<UUID, AnalyticsEventQueryPort.CategoryView> categories) {
        AnalyticsEventQueryPort.TypeView type = types.get(row.eventTypeId());
        AnalyticsEventQueryPort.SeverityView severity = severities.get(row.severityId());
        AnalyticsEventQueryPort.CategoryView category =
                type == null ? null : categories.get(type.eventCategoryId());
        return new AnalyticsTimelineResponse.TimelineItemDto(row.id(), row.deviceId(),
                type == null ? null : new AnalyticsTimelineResponse.EventTypeRef(
                        type.id(), type.code(), type.name()),
                category == null ? null : new AnalyticsTimelineResponse.EventCategoryRef(
                        category.id(), category.code(), category.name()),
                severity == null ? null : new AnalyticsTimelineResponse.SeverityRef(
                        severity.id(), severity.code(), severity.name(), severity.priority()),
                row.occurredAt(), row.soundPatternId(), row.metadata(),
                row.isOfflineSync(), row.hasEvidence(),
                row.mediaTypeId() == null ? null
                        : new AnalyticsTimelineResponse.MediaTypeRef(row.mediaTypeId(),
                                row.mediaTypeCode(), row.mediaTypeName(), row.mediaTypeMime()),
                row.createdAt());
    }
}
