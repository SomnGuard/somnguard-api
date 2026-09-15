package com.somnguard.parameterization.adapter.out.persistence.repository;

import com.somnguard.parameterization.adapter.out.persistence.entity.GlobalConfigEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GlobalConfigRepository extends JpaRepository<GlobalConfigEntity, Short> {

    /**
     * Fila singleton con bloqueo pesimista para serializar el bump (ADR-011 §4).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from GlobalConfigEntity g where g.id = :id")
    Optional<GlobalConfigEntity> lockById(@Param("id") Short id);
}
