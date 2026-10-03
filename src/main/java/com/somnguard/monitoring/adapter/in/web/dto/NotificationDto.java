package com.somnguard.monitoring.adapter.in.web.dto;

import com.somnguard.monitoring.adapter.out.persistence.entity.NotificationEntity;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DTO de notificación (HU-API-009 AC-002/AC-003, in-app + push).
 */
public record NotificationDto(
        UUID id,
        UUID alertLogId,
        String title,
        String message,
        String channel,
        String status,
        String statusCategory,
        short retryCount,
        OffsetDateTime sentAt,
        OffsetDateTime deliveredAt,
        OffsetDateTime readAt,
        OffsetDateTime createdAt
) {
    public static NotificationDto from(NotificationEntity entity) {
        return new NotificationDto(entity.getId(), entity.getAlertLogId(),
                entity.getTitle(), entity.getMessage(), entity.getChannel(),
                entity.getStatus(), entity.getStatusCategory(),
                entity.getRetryCount() == null ? 0 : entity.getRetryCount(),
                entity.getSentAt(), entity.getDeliveredAt(), entity.getReadAt(),
                entity.getCreatedAt());
    }
}
