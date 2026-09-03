package com.somnguard.parameterization.adapter.out.persistence.repository;

import com.somnguard.parameterization.adapter.out.persistence.entity.SoundPatternEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SoundPatternRepository extends JpaRepository<SoundPatternEntity, UUID> {

    Optional<SoundPatternEntity> findByCodeAndIsActiveTrue(String code);

    List<SoundPatternEntity> findByIsActiveTrue();
}
