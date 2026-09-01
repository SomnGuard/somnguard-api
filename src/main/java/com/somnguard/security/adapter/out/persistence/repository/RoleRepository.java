package com.somnguard.security.adapter.out.persistence.repository;

import com.somnguard.security.adapter.out.persistence.entity.RoleEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RoleRepository extends JpaRepository<RoleEntity, UUID> {

    Optional<RoleEntity> findByCodeAndIsActiveTrue(String code);
}
