package com.somnguard.security.adapter.out.persistence.repository;

import com.somnguard.security.adapter.out.persistence.entity.RoleFeatureEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RoleFeatureRepository extends JpaRepository<RoleFeatureEntity, UUID> {

    boolean existsByRoleIdAndFeatureIdAndDeletedAtIsNull(UUID roleId, UUID featureId);

    Optional<RoleFeatureEntity> findByRoleIdAndFeatureIdAndDeletedAtIsNull(UUID roleId, UUID featureId);
}
