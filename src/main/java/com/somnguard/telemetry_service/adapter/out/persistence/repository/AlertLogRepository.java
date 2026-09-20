package com.somnguard.telemetry_service.adapter.out.persistence.repository;

import com.somnguard.telemetry_service.adapter.out.persistence.entity.AlertLogEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AlertLogRepository extends JpaRepository<AlertLogEntity, UUID> {

    List<AlertLogEntity> findByEventId(UUID eventId);
}
