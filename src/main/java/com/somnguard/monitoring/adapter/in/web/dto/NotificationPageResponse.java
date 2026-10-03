package com.somnguard.monitoring.adapter.in.web.dto;

import java.util.List;

/**
 * Envelope paginado de notificaciones (HU-API-009, espejo de {@code EventPageResponse}).
 */
public record NotificationPageResponse(
        List<NotificationDto> data,
        Pagination pagination
) {
    public record Pagination(int page, int pageSize, long totalItems, int totalPages) {}
}
