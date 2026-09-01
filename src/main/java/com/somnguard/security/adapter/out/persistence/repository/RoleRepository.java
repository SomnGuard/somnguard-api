package com.somnguard.security.adapter.out.persistence.repository;

import com.somnguard.security.adapter.out.persistence.entity.RoleEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RoleRepository extends JpaRepository<RoleEntity, UUID> {

    Optional<RoleEntity> findByCodeAndIsActiveTrue(String code);

    @Query("""
            SELECT r.code FROM RoleEntity r
            WHERE r.id IN :roleIds
              AND r.isActive = true
            """)
    List<String> findActiveCodesByIds(@Param("roleIds") List<UUID> roleIds);

    @Query(value = """
            SELECT r.code FROM security.role r
            JOIN security.user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = :userId
              AND ur.is_active = true
              AND ur.deleted_at IS NULL
              AND (ur.expires_at IS NULL OR ur.expires_at > NOW())
              AND r.is_active = true
            """, nativeQuery = true)
    List<String> findActiveCodesByUserId(@Param("userId") UUID userId);
}
