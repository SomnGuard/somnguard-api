package com.somnguard.parameterization.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Versión global de configuración (ADR-011, singleton id = 1).
 * Todo POST/PATCH/DELETE efectivo en sound_pattern/event_type hace version++ en la
 * misma transacción desde la API (SELECT ... FOR UPDATE); sin triggers ni fan-out.
 * Regla: device.applied_config_version &lt; global_config.version =&gt; desactualizado.
 * DDL canónico en {@code somnguard-db/01_ddl/03_tables/034_*}.
 */
@Entity
@Table(name = "global_config", schema = "parameterization")
public class GlobalConfigEntity {

    public static final short SINGLETON_ID = 1;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private Short id = SINGLETON_ID;

    @Column(name = "version", nullable = false)
    private Integer version = 1;

    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    public GlobalConfigEntity() {}

    public Short getId() { return id; }
    public void setId(Short id) { this.id = id; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public UUID getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(UUID updatedBy) { this.updatedBy = updatedBy; }
}
