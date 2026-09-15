package com.somnguard.parameterization.application.usecase;

import com.somnguard.device_management.domain.service.DeviceConfigMergeService;
import com.somnguard.parameterization.adapter.out.persistence.entity.EventTypeEntity;
import com.somnguard.parameterization.adapter.out.persistence.entity.GlobalConfigEntity;
import com.somnguard.parameterization.adapter.out.persistence.entity.GlobalConfigHistoryEntity;
import com.somnguard.parameterization.adapter.out.persistence.entity.SoundPatternEntity;
import com.somnguard.parameterization.adapter.out.persistence.repository.EventTypeRepository;
import com.somnguard.parameterization.adapter.out.persistence.repository.GlobalConfigHistoryRepository;
import com.somnguard.parameterization.adapter.out.persistence.repository.GlobalConfigRepository;
import com.somnguard.parameterization.adapter.out.persistence.repository.SoundPatternRepository;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Versión global de configuración (ADR-011, solo global, bump en API, lazy).
 * La DB es la fuente de verdad: cada device guarda la última versión aplicada y
 * {@code applied < global} significa desactualizado. Sin fan-out: el heartbeat
 * y la app detectan por comparación.
 *
 * <p>Nota de módulos: el snapshot se construye con
 * {@link DeviceConfigMergeService} (función pura del catálogo vigente, sin overrides).
 * Es el mismo builder que {@code GET /devices/{id}/config} usa, así el snapshot
 * auditado y lo entregado al device son idénticos por construcción.
 */
@Service
public class GlobalConfigService {

    private final GlobalConfigRepository globalRepository;
    private final GlobalConfigHistoryRepository historyRepository;
    private final EventTypeRepository eventTypeRepository;
    private final SoundPatternRepository soundPatternRepository;

    public GlobalConfigService(GlobalConfigRepository globalRepository,
            GlobalConfigHistoryRepository historyRepository,
            EventTypeRepository eventTypeRepository,
            SoundPatternRepository soundPatternRepository) {
        this.globalRepository = globalRepository;
        this.historyRepository = historyRepository;
        this.eventTypeRepository = eventTypeRepository;
        this.soundPatternRepository = soundPatternRepository;
    }

    public record GlobalVersion(int version, OffsetDateTime updatedAt) {}
    public record ConfigStatus(boolean pending, int availableVersion) {}

    /**
     * Versión vigente, creándola (1,1) si aún no existe (p. ej. seed no corrido).
     */
    @Transactional
    public GlobalVersion getOrCreate() {
        GlobalConfigEntity g = globalRepository.findById(GlobalConfigEntity.SINGLETON_ID)
                .orElseGet(() -> {
                    GlobalConfigEntity created = new GlobalConfigEntity();
                    OffsetDateTime now = OffsetDateTime.now();
                    created.setVersion(1);
                    created.setUpdatedAt(now);
                    return globalRepository.save(created);
                });
        return new GlobalVersion(g.getVersion(), g.getUpdatedAt());
    }

    /**
     * Versión vigente en lectura (sin crear). Si la fila no existe se asume 1:
     * el próximo pull la materializa vía {@link #getOrCreate()}.
     */
    @Transactional(readOnly = true)
    public GlobalVersion current() {
        return globalRepository.findById(GlobalConfigEntity.SINGLETON_ID)
                .map(g -> new GlobalVersion(g.getVersion(), g.getUpdatedAt()))
                .orElseGet(() -> new GlobalVersion(1, null));
    }

    /**
     * Incrementa la versión global en la misma transacción del cambio de catálogo
     * (llamar DESPUÉS del save, dentro de la misma tx) y audita el snapshot completo.
     */
    @Transactional
    public GlobalVersion bump(UUID actor) {
        GlobalConfigEntity g = globalRepository.lockById(GlobalConfigEntity.SINGLETON_ID)
                .orElseGet(GlobalConfigEntity::new);
        OffsetDateTime now = OffsetDateTime.now();
        g.setVersion(g.getVersion() == null || g.getVersion() < 1 ? 2 : g.getVersion() + 1);
        g.setUpdatedAt(now);
        g.setUpdatedBy(actor);
        globalRepository.save(g);
        Map<String, Object> snapshot = buildSnapshot(g.getVersion());
        GlobalConfigHistoryEntity h = new GlobalConfigHistoryEntity();
        h.setId(UUID.randomUUID());
        h.setVersion(g.getVersion());
        h.setSnapshotJson(new LinkedHashMap<>(snapshot));
        h.setCreatedAt(now);
        h.setCreatedBy(actor);
        historyRepository.save(h);
        return new GlobalVersion(g.getVersion(), now);
    }

    /**
     * Estado para heartbeat/app: {@code pending = flag manual} (solo manual, sin lazy).
     *
     * @param appliedConfigVersion última versión aplicada por el device (null = 0, nunca)
     * @param pendingFlag          {@code device.pending_config_update} (gesto manual refresh)
     */
    @Transactional(readOnly = true)
    public ConfigStatus statusFor(Integer appliedConfigVersion, Boolean pendingFlag) {
        GlobalVersion global = current();
        int applied = appliedConfigVersion == null ? 0 : appliedConfigVersion;
        boolean pending = Boolean.TRUE.equals(pendingFlag); // SOLO manual, sin lazy
        return new ConfigStatus(pending, global.version());
    }

    /**
     * Snapshot global puro (sin overrides): idéntico a lo que el GET entrega.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> buildSnapshot(int version) {
        List<EventTypeEntity> events = eventTypeRepository.findByDeletedAtIsNull();
        List<SoundPatternEntity> sounds = soundPatternRepository.findByIsActiveTrue();
        Map<String, Object> snapshot = new LinkedHashMap<>(
                DeviceConfigMergeService.buildEffective(Map.of(), events, sounds));
        snapshot.put("version", version);
        return snapshot;
    }
}
