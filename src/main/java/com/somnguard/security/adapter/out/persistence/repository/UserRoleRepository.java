package com.somnguard.security.adapter.out.persistence.repository;

import com.somnguard.security.adapter.out.persistence.entity.UserRoleEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRoleRepository extends JpaRepository<UserRoleEntity, UUID> {

    @Query("""
            SELECT ur.roleId FROM UserRoleEntity ur
            WHERE ur.userId = :userId
              AND ur.isActive = true
              AND ur.deletedAt IS NULL
              AND (ur.expiresAt IS NULL OR ur.expiresAt > :now)
            """)
    List<UUID> findActiveRoleIdsByUserId(@Param("userId") UUID userId, @Param("now") OffsetDateTime now);

    @Query("""
            SELECT ur FROM UserRoleEntity ur
            WHERE ur.userId = :userId
              AND ur.isActive = true
              AND ur.deletedAt IS NULL
              AND (ur.expiresAt IS NULL OR ur.expiresAt > :now)
            """)
    List<UserRoleEntity> findActiveByUserId(@Param("userId") UUID userId, @Param("now") OffsetDateTime now);
}
