package com.somnguard.parameterization.adapter.out.persistence.repository;

import com.somnguard.parameterization.adapter.out.persistence.entity.SeverityEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SeverityRepository extends JpaRepository<SeverityEntity, UUID> {

    Optional<SeverityEntity> findByCodeAndIsActiveTrue(String code);

    List<SeverityEntity> findByIsActiveTrueOrderByPriorityAsc();
}
