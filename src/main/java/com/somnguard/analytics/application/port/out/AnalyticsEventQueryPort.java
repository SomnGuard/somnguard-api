package com.somnguard.analytics.application.port.out;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Puerto de salida de Analytics (solo lectura).
 *
 * <p>Centraliza todo el acceso a datos de otros módulos para no dispersar
 * repositorios ajenos por los casos de uso. No expone entidades JPA: devuelve
 * filas y vistas inmutables. No crea ni modifica eventos, dispositivos ni
 * asignaciones.
 *
 * <p>Nota ADR-003: los módulos telemetría/dispositivos no exponen puertos de
 * entrada, por lo que este puerto se implementa con un adaptador de lectura
 * (JPA Criteria + {@code EventSpecifications} para paginación). No se usan las
 * vistas materializadas {@code analytics.v_*} al no existir estrategia de
 * refresco comprobada.
 */
public interface AnalyticsEventQueryPort {

    /** Dispositivos con asignación vigente para un usuario (vacío = sin acceso). */
    List<UUID> findOwnedDeviceIds(UUID userId);

    /**
     * Resuelve {@code severity} a UUID de catálogo ({@code null} = sin filtro).
     * Acepta UUID o código con normalización ES/EN; código desconocido lanza
     * {@code InvalidCatalogReferenceException} (422).
     */
    UUID resolveSeverityId(String severity);

    Page<TimelineRow> findTimeline(UUID deviceId, UUID eventTypeId, UUID severityId,
            OffsetDateTime from, OffsetDateTime to, List<UUID> deviceIds, Pageable pageable);

    long countEvents(UUID deviceId, UUID eventTypeId, UUID severityId,
            OffsetDateTime from, OffsetDateTime to, List<UUID> deviceIds);

    long countDistinctDevices(UUID deviceId, UUID eventTypeId, UUID severityId,
            OffsetDateTime from, OffsetDateTime to, List<UUID> deviceIds);

    List<TypeCount> countByType(UUID deviceId, UUID eventTypeId, UUID severityId,
            OffsetDateTime from, OffsetDateTime to, List<UUID> deviceIds);

    List<SeverityCount> countBySeverity(UUID deviceId, UUID eventTypeId, UUID severityId,
            OffsetDateTime from, OffsetDateTime to, List<UUID> deviceIds);

    long countOfflineSync(UUID deviceId, UUID eventTypeId, UUID severityId,
            OffsetDateTime from, OffsetDateTime to, List<UUID> deviceIds);

    Optional<OffsetDateTime> findEarliestOccurredAt(UUID deviceId, UUID eventTypeId, UUID severityId,
            OffsetDateTime from, OffsetDateTime to, List<UUID> deviceIds);

    Optional<OffsetDateTime> findLatestOccurredAt(UUID deviceId, UUID eventTypeId, UUID severityId,
            OffsetDateTime from, OffsetDateTime to, List<UUID> deviceIds);

    /** Marcas {@code occurred_at} ascendentes para la evolución diaria (rango acotado). */
    List<OffsetDateTime> findOccurredAts(UUID deviceId, UUID eventTypeId, UUID severityId,
            OffsetDateTime from, OffsetDateTime to, List<UUID> deviceIds);

    Map<UUID, TypeView> findTypeViews(Set<UUID> eventTypeIds);

    Map<UUID, SeverityView> findSeverityViews(Set<UUID> severityIds);

    Map<UUID, CategoryView> findCategoryViews(Set<UUID> eventCategoryIds);

    record TimelineRow(UUID id, UUID deviceId, UUID eventTypeId, UUID severityId,
            UUID soundPatternId, OffsetDateTime occurredAt, Boolean isOfflineSync,
            java.util.Map<String, Object> metadata, OffsetDateTime createdAt,
            boolean hasEvidence, UUID mediaTypeId,
            String mediaTypeCode, String mediaTypeName, String mediaTypeMime) {
    }

    record TypeCount(UUID eventTypeId, long count) {
    }

    record SeverityCount(UUID severityId, long count) {
    }

    record TypeView(UUID id, String code, String name, UUID eventCategoryId) {
    }

    record SeverityView(UUID id, String code, String name, short priority) {
    }

    record CategoryView(UUID id, String code, String name) {
    }
}
