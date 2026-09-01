package com.somnguard.security.adapter.out.persistence.repository;

import com.somnguard.security.adapter.out.persistence.entity.FeatureEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface FeatureRepository extends JpaRepository<FeatureEntity, UUID> {

    Optional<FeatureEntity> findByCode(String code);

    List<FeatureEntity> findByModuleId(UUID moduleId);

    @Query(value = """
        SELECT f.code FROM security.feature f
        JOIN security.role_feature rf ON rf.feature_id = f.id AND rf.deleted_at IS NULL AND rf.is_active = true
        JOIN security.user_role ur ON ur.role_id = rf.role_id AND ur.deleted_at IS NULL AND ur.is_active = true
        WHERE ur.user_id = :userId
        """, nativeQuery = true)
    List<String> findCodesByUserId(UUID userId);
}
