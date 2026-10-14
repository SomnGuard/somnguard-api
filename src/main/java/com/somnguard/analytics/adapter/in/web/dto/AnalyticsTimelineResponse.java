package com.somnguard.analytics.adapter.in.web.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Línea de tiempo paginada de eventos de conducción.
 * Espejo del envelope {@code EventPageResponse}: Jackson global
 * {@code SNAKE_CASE} serializa como
 * {@code {data, pagination:{page,page_size,total_items,total_pages}}}.
 * No expone rutas internas de almacenamiento ni entidades JPA.
 */
public record AnalyticsTimelineResponse(
        List<TimelineItemDto> data,
        Pagination pagination
) {
    public record TimelineItemDto(
            UUID id,
            UUID deviceId,
            EventTypeRef eventType,
            EventCategoryRef eventCategory,
            SeverityRef severity,
            OffsetDateTime occurredAt,
            UUID soundPatternId,
            Map<String, Object> metadata,
            Boolean isOfflineSync,
            Boolean hasEvidence,
            MediaTypeRef mediaType,
            OffsetDateTime createdAt
    ) {
    }

    public record EventTypeRef(UUID id, String code, String name) {
    }

    public record EventCategoryRef(UUID id, String code, String name) {
    }

    public record SeverityRef(UUID id, String code, String name, short priority) {
    }

    public record MediaTypeRef(UUID id, String code, String name, String mimeType) {
    }

    public record Pagination(int page, int pageSize, long totalItems, int totalPages) {
    }
}
