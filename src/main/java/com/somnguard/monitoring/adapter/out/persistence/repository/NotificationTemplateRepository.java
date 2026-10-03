package com.somnguard.monitoring.adapter.out.persistence.repository;

import com.somnguard.monitoring.adapter.out.persistence.entity.NotificationTemplateEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplateEntity, UUID> {

    Optional<NotificationTemplateEntity> findByEventTypeCodeAndSeverityCodeAndChannelAndIsActiveTrue(
            String eventTypeCode, String severityCode, String channel);
}
