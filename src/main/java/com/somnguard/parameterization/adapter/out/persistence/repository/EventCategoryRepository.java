package com.somnguard.parameterization.adapter.out.persistence.repository;

import com.somnguard.parameterization.adapter.out.persistence.entity.EventCategoryEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EventCategoryRepository extends JpaRepository<EventCategoryEntity, UUID> {

    Optional<EventCategoryEntity> findByCodeAndIsActiveTrue(String code);

    List<EventCategoryEntity> findByIsActiveTrueOrderBySortOrderAsc();
}
