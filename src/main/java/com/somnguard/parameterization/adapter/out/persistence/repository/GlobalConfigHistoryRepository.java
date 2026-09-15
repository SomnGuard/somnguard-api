package com.somnguard.parameterization.adapter.out.persistence.repository;

import com.somnguard.parameterization.adapter.out.persistence.entity.GlobalConfigHistoryEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GlobalConfigHistoryRepository extends JpaRepository<GlobalConfigHistoryEntity, UUID> {

    Optional<GlobalConfigHistoryEntity> findByVersion(Integer version);
}
