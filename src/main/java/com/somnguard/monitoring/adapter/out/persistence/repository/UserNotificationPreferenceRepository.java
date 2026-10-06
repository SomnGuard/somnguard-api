package com.somnguard.monitoring.adapter.out.persistence.repository;

import com.somnguard.monitoring.adapter.out.persistence.entity.UserNotificationPreferenceEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserNotificationPreferenceRepository
        extends JpaRepository<UserNotificationPreferenceEntity, UUID> {
}
