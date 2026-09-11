package com.somnguard.device_management.adapter.out.persistence.repository;

import com.somnguard.device_management.adapter.out.persistence.entity.ProvisioningTokenEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProvisioningTokenRepository extends JpaRepository<ProvisioningTokenEntity, UUID> {

    Optional<ProvisioningTokenEntity> findByTokenHash(String tokenHash);
}
