package com.somnguard.telemetry_service.adapter.out.persistence.repository;

import com.somnguard.telemetry_service.adapter.out.persistence.entity.EvidenceEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EvidenceRepository extends JpaRepository<EvidenceEntity, UUID> {

    Optional<EvidenceEntity> findByEventId(UUID eventId);

    boolean existsByEventId(UUID eventId);
}
