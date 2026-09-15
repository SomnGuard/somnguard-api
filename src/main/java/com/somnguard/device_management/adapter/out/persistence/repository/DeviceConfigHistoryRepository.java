package com.somnguard.device_management.adapter.out.persistence.repository;

import com.somnguard.device_management.adapter.out.persistence.entity.DeviceConfigHistoryEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeviceConfigHistoryRepository extends JpaRepository<DeviceConfigHistoryEntity, UUID> {

    List<DeviceConfigHistoryEntity> findByDeviceConfigIdOrderByCreatedAtDesc(UUID deviceConfigId);

    Page<DeviceConfigHistoryEntity> findByDeviceConfigId(UUID deviceConfigId, Pageable pageable);
}
