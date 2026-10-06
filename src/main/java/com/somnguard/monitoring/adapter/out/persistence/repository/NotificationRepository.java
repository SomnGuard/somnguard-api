package com.somnguard.monitoring.adapter.out.persistence.repository;

import com.somnguard.monitoring.adapter.out.persistence.entity.NotificationEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {

    Page<NotificationEntity> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    long countByUserIdAndReadAtIsNullAndDeletedAtIsNull(UUID userId);

    List<NotificationEntity> findByStatusAndNextRetryAtBeforeAndDeletedAtIsNull(
            String status, OffsetDateTime before);
}
