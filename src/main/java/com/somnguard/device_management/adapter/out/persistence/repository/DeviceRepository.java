package com.somnguard.device_management.adapter.out.persistence.repository;

import com.somnguard.device_management.adapter.out.persistence.entity.DeviceEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeviceRepository extends JpaRepository<DeviceEntity, UUID> {

    Optional<DeviceEntity> findByIdAndDeletedAtIsNull(UUID id);

    Optional<DeviceEntity> findBySerialNumberAndDeletedAtIsNull(String serialNumber);

    Optional<DeviceEntity> findByClaimCodeAndDeletedAtIsNull(String claimCode);

    Page<DeviceEntity> findByDeletedAtIsNull(Pageable pageable);

    Page<DeviceEntity> findByStatusAndDeletedAtIsNull(String status, Pageable pageable);

    Page<DeviceEntity> findByIdInAndDeletedAtIsNull(List<UUID> ids, Pageable pageable);

    Page<DeviceEntity> findByIdInAndStatusAndDeletedAtIsNull(List<UUID> ids, String status, Pageable pageable);

    List<DeviceEntity> findByStatusAndLastHeartbeatAtBeforeAndDeletedAtIsNull(String status, OffsetDateTime before);
}
