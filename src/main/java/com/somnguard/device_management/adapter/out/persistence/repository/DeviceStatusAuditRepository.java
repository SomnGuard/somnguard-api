package com.somnguard.device_management.adapter.out.persistence.repository;

import com.somnguard.device_management.adapter.out.persistence.entity.DeviceStatusAuditEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeviceStatusAuditRepository extends JpaRepository<DeviceStatusAuditEntity, Long> {
    void deleteByDeviceId(UUID deviceId);
}
