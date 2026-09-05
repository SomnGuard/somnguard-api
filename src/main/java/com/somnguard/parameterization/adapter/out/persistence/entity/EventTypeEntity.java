package com.somnguard.parameterization.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "event_type", schema = "parameterization")
public class EventTypeEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "code", nullable = false, length = 30)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "event_category_id", nullable = false)
    private UUID eventCategoryId;

    @Column(name = "default_severity_id", nullable = false)
    private UUID defaultSeverityId;

    @Column(name = "default_sound_pattern_id", nullable = false)
    private UUID defaultSoundPatternId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "threshold_config", nullable = false)
    private Map<String, Object> thresholdConfig;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "status", nullable = false, length = 50)
    private String status = "DRAFT";

    @Column(name = "status_category", nullable = false, length = 30)
    private String statusCategory = "PENDING";

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "deleted_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    @Column(name = "version", nullable = false)
    private Integer version = 1;

    public EventTypeEntity() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public UUID getEventCategoryId() { return eventCategoryId; }
    public void setEventCategoryId(UUID eventCategoryId) { this.eventCategoryId = eventCategoryId; }
    public UUID getDefaultSeverityId() { return defaultSeverityId; }
    public void setDefaultSeverityId(UUID defaultSeverityId) { this.defaultSeverityId = defaultSeverityId; }
    public UUID getDefaultSoundPatternId() { return defaultSoundPatternId; }
    public void setDefaultSoundPatternId(UUID id) { this.defaultSoundPatternId = id; }
    public Map<String, Object> getThresholdConfig() { return thresholdConfig; }
    public void setThresholdConfig(Map<String, Object> thresholdConfig) { this.thresholdConfig = thresholdConfig; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getStatusCategory() { return statusCategory; }
    public void setStatusCategory(String statusCategory) { this.statusCategory = statusCategory; }
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
}
