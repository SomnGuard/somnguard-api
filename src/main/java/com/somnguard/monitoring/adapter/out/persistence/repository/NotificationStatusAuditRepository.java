package com.somnguard.monitoring.adapter.out.persistence.repository;

import com.somnguard.monitoring.adapter.out.persistence.entity.NotificationStatusAuditEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationStatusAuditRepository extends JpaRepository<NotificationStatusAuditEntity, Long> {

    long countByNotificationId(UUID notificationId);
}
