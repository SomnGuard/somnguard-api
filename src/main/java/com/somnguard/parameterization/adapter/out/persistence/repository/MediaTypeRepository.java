package com.somnguard.parameterization.adapter.out.persistence.repository;

import com.somnguard.parameterization.adapter.out.persistence.entity.MediaTypeEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MediaTypeRepository extends JpaRepository<MediaTypeEntity, UUID> {

    Optional<MediaTypeEntity> findByCodeAndIsActiveTrue(String code);

    List<MediaTypeEntity> findByIsActiveTrue();
}
