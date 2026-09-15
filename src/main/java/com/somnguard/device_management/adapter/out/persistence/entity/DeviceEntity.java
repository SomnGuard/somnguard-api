package com.somnguard.device_management.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "device", schema = "device_management")
public class DeviceEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "serial_number", nullable = false, length = 100, unique = true)
    private String serialNumber;

    @Column(name = "api_key_hash", nullable = false, columnDefinition = "TEXT")
    private String apiKeyHash;

    @Column(name = "firmware_version", length = 50)
    private String firmwareVersion;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "last_heartbeat_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime lastHeartbeatAt;

    @Column(name = "last_sync_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime lastSyncAt;

    @Column(name = "last_config_pull_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime lastConfigPullAt;

    @Column(name = "pending_config_update", nullable = false)
    private Boolean pendingConfigUpdate = false;

    @Column(name = "applied_config_version", nullable = false)
    private Integer appliedConfigVersion = 0;

    @Column(name = "last_seen_ip", length = 45)
    private String lastSeenIp;

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

    @Column(name = "status", length = 50)
    private String status;

    @Column(name = "status_category", length = 30)
    private String statusCategory;

    // Enmienda ADR-010 (RF-DEV-12): claim_code público reutilizable, permanente por device.
    // Solo reclamable en REGISTERED; unassign libera (claimed_at NULL) y el mismo código re-sirve.
    @Column(name = "claim_code", nullable = false, length = 20)
    private String claimCode;

    @Column(name = "claimed_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime claimedAt;

    @Column(name = "provisioning_token_id")
    private UUID provisioningTokenId;

    public DeviceEntity() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }
    public String getApiKeyHash() { return apiKeyHash; }
    public void setApiKeyHash(String apiKeyHash) { this.apiKeyHash = apiKeyHash; }
    public String getFirmwareVersion() { return firmwareVersion; }
    public void setFirmwareVersion(String firmwareVersion) { this.firmwareVersion = firmwareVersion; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    public OffsetDateTime getLastHeartbeatAt() { return lastHeartbeatAt; }
    public void setLastHeartbeatAt(OffsetDateTime lastHeartbeatAt) { this.lastHeartbeatAt = lastHeartbeatAt; }
    public OffsetDateTime getLastSyncAt() { return lastSyncAt; }
    public void setLastSyncAt(OffsetDateTime lastSyncAt) { this.lastSyncAt = lastSyncAt; }
    public OffsetDateTime getLastConfigPullAt() { return lastConfigPullAt; }
    public void setLastConfigPullAt(OffsetDateTime lastConfigPullAt) { this.lastConfigPullAt = lastConfigPullAt; }
    public Boolean getPendingConfigUpdate() { return pendingConfigUpdate; }
    public void setPendingConfigUpdate(Boolean pendingConfigUpdate) { this.pendingConfigUpdate = pendingConfigUpdate; }
    public Integer getAppliedConfigVersion() { return appliedConfigVersion; }
    public void setAppliedConfigVersion(Integer appliedConfigVersion) { this.appliedConfigVersion = appliedConfigVersion; }
    public String getLastSeenIp() { return lastSeenIp; }
    public void setLastSeenIp(String lastSeenIp) { this.lastSeenIp = lastSeenIp; }
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
    public String getClaimCode() { return claimCode; }
    public void setClaimCode(String claimCode) { this.claimCode = claimCode; }
    public OffsetDateTime getClaimedAt() { return claimedAt; }
    public void setClaimedAt(OffsetDateTime claimedAt) { this.claimedAt = claimedAt; }
    public UUID getProvisioningTokenId() { return provisioningTokenId; }
    public void setProvisioningTokenId(UUID provisioningTokenId) { this.provisioningTokenId = provisioningTokenId; }
}
