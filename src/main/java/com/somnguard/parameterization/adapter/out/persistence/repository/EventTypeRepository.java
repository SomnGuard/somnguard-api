package com.somnguard.parameterization.adapter.out.persistence.repository;

import com.somnguard.parameterization.adapter.out.persistence.entity.EventTypeEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EventTypeRepository extends JpaRepository<EventTypeEntity, UUID> {

    Optional<EventTypeEntity> findByCodeAndDeletedAtIsNull(String code);

    List<EventTypeEntity> findByDeletedAtIsNull();

    List<EventTypeEntity> findByEventCategoryIdAndDeletedAtIsNull(UUID eventCategoryId);
}
