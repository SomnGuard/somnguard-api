package com.somnguard.monitoring.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Espejo JPA de {@code monitoring.device_token} (HU-API-009 AC-002, HU-APP-002 AC-001).
 * Tokens push FCM/APNs por usuario; {@code token} único global.
 */
@Entity
@Table(name = "device_token", schema = "monitoring")
public class DeviceTokenEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token", nullable = false, columnDefinition = "TEXT")
    private String token;

    @Column(name = "platform", nullable = false, length = 20)
    private String platform;

    @Column(name = "app_version", length = 30)
    private String appVersion;

    @Column(name = "locale", length = 10)
    private String locale;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "last_seen_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime lastSeenAt;

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

    @Version
    @Column(name = "version", nullable = false)
    private Integer version = 1;

    public DeviceTokenEntity() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    public String getAppVersion() { return appVersion; }
    public void setAppVersion(String appVersion) { this.appVersion = appVersion; }
    public String getLocale() { return locale; }
    public void setLocale(String locale) { this.locale = locale; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    public OffsetDateTime getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(OffsetDateTime lastSeenAt) { this.lastSeenAt = lastSeenAt; }
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
