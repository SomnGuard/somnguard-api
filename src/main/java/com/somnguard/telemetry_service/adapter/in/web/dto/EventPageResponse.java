package com.somnguard.telemetry_service.adapter.in.web.dto;

import java.util.List;

/**
 * Envelope paginado HU-API-008 AC-002 (FEA-TEL-QUERY).
 * Espejo de {@code DevicePageResponse}; Jackson global {@code SNAKE_CASE}
 * serializa como {@code {data, pagination:{page,page_size,total_items,total_pages}}}.
 */
public record EventPageResponse(
        List<EventDetailDto> data,
        Pagination pagination
) {
    public record Pagination(int page, int pageSize, long totalItems, int totalPages) {}
}
