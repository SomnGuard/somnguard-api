package com.somnguard.security.adapter.out.persistence.repository;

import com.somnguard.security.adapter.out.persistence.entity.UserRoleEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRoleRepository extends JpaRepository<UserRoleEntity, UUID> {
}
