package com.somnguard.telemetry_service.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Espejo JPA de {@code telemetry_service.alert_log} (HU-API-007 AC-005).
 * Append-only: un registro por evento con código AS-XX (sound_pattern), timestamp y severidad.
 */
@Entity
@Table(name = "alert_log", schema = "telemetry_service")
public class AlertLogEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "sound_pattern_id", nullable = false)
    private UUID soundPatternId;

    @Column(name = "severity_id", nullable = false)
    private UUID severityId;

    @Column(name = "triggered_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime triggeredAt;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime createdAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    public AlertLogEntity() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getEventId() { return eventId; }
    public void setEventId(UUID eventId) { this.eventId = eventId; }
    public UUID getSoundPatternId() { return soundPatternId; }
    public void setSoundPatternId(UUID soundPatternId) { this.soundPatternId = soundPatternId; }
    public UUID getSeverityId() { return severityId; }
    public void setSeverityId(UUID severityId) { this.severityId = severityId; }
    public OffsetDateTime getTriggeredAt() { return triggeredAt; }
    public void setTriggeredAt(OffsetDateTime triggeredAt) { this.triggeredAt = triggeredAt; }
    public UUID getDeviceId() { return deviceId; }
    public void setDeviceId(UUID deviceId) { this.deviceId = deviceId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
}
