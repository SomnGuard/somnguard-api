package com.somnguard.device_management.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Historial append-only de cambios manuales (HU-API-005 AC-004).
 * Solo se inserta desde PATCH manual; el GET y los cambios de catálogo no generan filas.
 * DDL canónico en {@code somnguard-db/01_ddl/03_tables/020_*}.
 */
@Entity
@Table(name = "device_config_history", schema = "device_management")
public class DeviceConfigHistoryEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "device_config_id", nullable = false)
    private UUID deviceConfigId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "configuration", nullable = false)
    private Map<String, Object> configuration;

    @Column(name = "changed_by", nullable = false)
    private UUID changedBy;

    @Column(name = "change_reason", length = 200)
    private String changeReason;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    public DeviceConfigHistoryEntity() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getDeviceConfigId() { return deviceConfigId; }
    public void setDeviceConfigId(UUID deviceConfigId) { this.deviceConfigId = deviceConfigId; }
    public Map<String, Object> getConfiguration() { return configuration; }
    public void setConfiguration(Map<String, Object> configuration) { this.configuration = configuration; }
    public UUID getChangedBy() { return changedBy; }
    public void setChangedBy(UUID changedBy) { this.changedBy = changedBy; }
    public String getChangeReason() { return changeReason; }
    public void setChangeReason(String changeReason) { this.changeReason = changeReason; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
}
