package com.somnguard.security.adapter.out.persistence.repository;

import com.somnguard.security.adapter.out.persistence.entity.EmailVerificationEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmailVerificationRepository extends JpaRepository<EmailVerificationEntity, UUID> {
    Optional<EmailVerificationEntity> findByTokenHashAndIsUsedFalseAndIsActiveTrue(String tokenHash);
}
