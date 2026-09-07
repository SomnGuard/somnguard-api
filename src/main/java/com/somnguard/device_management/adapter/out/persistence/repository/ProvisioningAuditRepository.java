package com.somnguard.device_management.adapter.out.persistence.repository;

import com.somnguard.device_management.adapter.out.persistence.entity.ProvisioningAuditEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProvisioningAuditRepository extends JpaRepository<ProvisioningAuditEntity, UUID> {
}
