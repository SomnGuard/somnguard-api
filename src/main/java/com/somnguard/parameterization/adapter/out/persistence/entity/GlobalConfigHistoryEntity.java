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

/**
 * Historial de versiones globales (ADR-011, append-only, solo INSERT).
 * Un snapshot completo por versión: lo mismo que {@code GET /devices/{id}/config} devuelve.
 * DDL canónico en {@code somnguard-db/01_ddl/03_tables/035_*}.
 */
@Entity
@Table(name = "global_config_history", schema = "parameterization")
public class GlobalConfigHistoryEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "version", nullable = false, unique = true)
    private Integer version;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot_json", nullable = false)
    private Map<String, Object> snapshotJson;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    public GlobalConfigHistoryEntity() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public Map<String, Object> getSnapshotJson() { return snapshotJson; }
    public void setSnapshotJson(Map<String, Object> snapshotJson) { this.snapshotJson = snapshotJson; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
}
