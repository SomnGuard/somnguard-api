package com.somnguard.telemetry_service.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Espejo JPA de {@code telemetry_service.event} (HU-DB-001a, HU-API-007 AC-003).
 * El esquema lo aplica somnguard-db (Liquibase); aquí solo {@code ddl-auto: validate}.
 * El PK {@code id} es el {@code event_id} UUIDv7 generado en el edge (idempotencia RN-08).
 */
@Entity
@Table(name = "event", schema = "telemetry_service")
public class EventEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "event_type_id", nullable = false)
    private UUID eventTypeId;

    @Column(name = "occurred_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime occurredAt;

    @Column(name = "severity_id", nullable = false)
    private UUID severityId;

    @Column(name = "sound_pattern_id")
    private UUID soundPatternId;

    @Column(name = "is_offline_sync", nullable = false)
    private Boolean isOfflineSync = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime createdAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "deleted_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version = 1;

    @Column(name = "status", length = 50)
    private String status;

    @Column(name = "status_category", length = 30)
    private String statusCategory;

    public EventEntity() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getDeviceId() { return deviceId; }
    public void setDeviceId(UUID deviceId) { this.deviceId = deviceId; }
    public UUID getEventTypeId() { return eventTypeId; }
    public void setEventTypeId(UUID eventTypeId) { this.eventTypeId = eventTypeId; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(OffsetDateTime occurredAt) { this.occurredAt = occurredAt; }
    public UUID getSeverityId() { return severityId; }
    public void setSeverityId(UUID severityId) { this.severityId = severityId; }
    public UUID getSoundPatternId() { return soundPatternId; }
    public void setSoundPatternId(UUID soundPatternId) { this.soundPatternId = soundPatternId; }
    public Boolean getIsOfflineSync() { return isOfflineSync; }
    public void setIsOfflineSync(Boolean isOfflineSync) { this.isOfflineSync = isOfflineSync; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public UUID getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(UUID updatedBy) { this.updatedBy = updatedBy; }
    public OffsetDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(OffsetDateTime deletedAt) { this.deletedAt = deletedAt; }
    public UUID getDeletedBy() { return deletedBy; }
    public void setDeletedBy(UUID deletedBy) { this.deletedBy = deletedBy; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getStatusCategory() { return statusCategory; }
    public void setStatusCategory(String statusCategory) { this.statusCategory = statusCategory; }
}
