package com.somnguard.monitoring.adapter.in.web.dto;

import com.somnguard.monitoring.adapter.out.persistence.entity.UserNotificationPreferenceEntity;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Preferencias de notificación (HU-API-009 AC-004).
 */
public record PreferenceDto(
        UUID userId,
        boolean pushEnabled,
        boolean emailEnabled,
        boolean inAppEnabled,
        LocalTime quietHoursStart,
        LocalTime quietHoursEnd,
        String timezone,
        String minSeverityCode
) {
    public static PreferenceDto from(UserNotificationPreferenceEntity entity) {
        return new PreferenceDto(entity.getUserId(),
                Boolean.TRUE.equals(entity.getPushEnabled()),
                Boolean.TRUE.equals(entity.getEmailEnabled()),
                Boolean.TRUE.equals(entity.getInAppEnabled()),
                entity.getQuietHoursStart(), entity.getQuietHoursEnd(),
                entity.getTimezone(), entity.getMinSeverityCode());
    }
}
