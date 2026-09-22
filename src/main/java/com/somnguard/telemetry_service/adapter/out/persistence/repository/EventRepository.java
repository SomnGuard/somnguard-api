package com.somnguard.telemetry_service.adapter.out.persistence.repository;

import com.somnguard.telemetry_service.adapter.out.persistence.entity.EventEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EventRepository extends JpaRepository<EventEntity, UUID> {

    Optional<EventEntity> findByIdAndDeletedAtIsNull(UUID id);
}
