package com.somnguard.security.adapter.out.persistence.repository;

import com.somnguard.security.adapter.out.persistence.entity.UserEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    boolean existsByEmailAndDeletedAtIsNull(String email);

    boolean existsByPhoneAndDeletedAtIsNull(String phone);

    Optional<UserEntity> findByEmailAndDeletedAtIsNull(String email);
}
