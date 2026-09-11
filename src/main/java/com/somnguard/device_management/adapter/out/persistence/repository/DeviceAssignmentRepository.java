package com.somnguard.device_management.adapter.out.persistence.repository;

import com.somnguard.device_management.adapter.out.persistence.entity.DeviceAssignmentEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeviceAssignmentRepository extends JpaRepository<DeviceAssignmentEntity, UUID> {

    Optional<DeviceAssignmentEntity> findByDeviceIdAndUnassignedAtIsNullAndDeletedAtIsNull(UUID deviceId);

    List<DeviceAssignmentEntity> findByUserIdAndUnassignedAtIsNullAndDeletedAtIsNull(UUID userId);

    List<DeviceAssignmentEntity> findByAssignedAtBetween(OffsetDateTime from, OffsetDateTime to);

    List<DeviceAssignmentEntity> findByUserIdAndAssignedAtBetween(UUID userId, OffsetDateTime from, OffsetDateTime to);
}
