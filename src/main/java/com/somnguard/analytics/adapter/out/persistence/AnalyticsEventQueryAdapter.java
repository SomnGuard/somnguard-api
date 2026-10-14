package com.somnguard.analytics.adapter.out.persistence;

import com.somnguard.analytics.application.port.out.AnalyticsEventQueryPort;
import com.somnguard.device_management.adapter.out.persistence.repository.DeviceAssignmentRepository;
import com.somnguard.parameterization.adapter.out.persistence.entity.EventTypeEntity;
import com.somnguard.parameterization.adapter.out.persistence.entity.MediaTypeEntity;
import com.somnguard.parameterization.adapter.out.persistence.entity.SeverityEntity;
import com.somnguard.parameterization.adapter.out.persistence.repository.EventCategoryRepository;
import com.somnguard.parameterization.adapter.out.persistence.repository.EventTypeRepository;
import com.somnguard.parameterization.adapter.out.persistence.repository.MediaTypeRepository;
import com.somnguard.parameterization.adapter.out.persistence.repository.SeverityRepository;
import com.somnguard.telemetry_service.adapter.out.persistence.entity.EventEntity;
import com.somnguard.telemetry_service.adapter.out.persistence.entity.EvidenceEntity;
import com.somnguard.telemetry_service.adapter.out.persistence.repository.EventRepository;
import com.somnguard.telemetry_service.adapter.out.persistence.repository.EventSpecifications;
import com.somnguard.telemetry_service.adapter.out.persistence.repository.EvidenceRepository;
import com.somnguard.telemetry_service.domain.exception.InvalidCatalogReferenceException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador de lectura de Analytics.
 *
 * <p>Implementa {@link AnalyticsEventQueryPort} con consultas de solo lectura
 * sobre las tablas actuales (eventos, evidencias y catálogos) mediante JPA
 * Criteria para agregaciones en base de datos y {@code EventSpecifications}
 * para la paginación. No escribe nada y no usa las vistas materializadas
 * {@code analytics.v_*} (sin refresco comprobado).
 */
@Repository
@Transactional(readOnly = true)
public class AnalyticsEventQueryAdapter implements AnalyticsEventQueryPort {

    private final EventRepository eventRepository;
    private final EvidenceRepository evidenceRepository;
    private final EventTypeRepository eventTypeRepository;
    private final SeverityRepository severityRepository;
    private final EventCategoryRepository eventCategoryRepository;
    private final MediaTypeRepository mediaTypeRepository;
    private final DeviceAssignmentRepository assignmentRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public AnalyticsEventQueryAdapter(EventRepository eventRepository,
            EvidenceRepository evidenceRepository,
            EventTypeRepository eventTypeRepository,
            SeverityRepository severityRepository,
            EventCategoryRepository eventCategoryRepository,
            MediaTypeRepository mediaTypeRepository,
            DeviceAssignmentRepository assignmentRepository) {
        this.eventRepository = eventRepository;
        this.evidenceRepository = evidenceRepository;
        this.eventTypeRepository = eventTypeRepository;
        this.severityRepository = severityRepository;
        this.eventCategoryRepository = eventCategoryRepository;
        this.mediaTypeRepository = mediaTypeRepository;
        this.assignmentRepository = assignmentRepository;
    }

    @Override
    public List<UUID> findOwnedDeviceIds(UUID userId) {
        if (userId == null) {
            return List.of();
        }
        return assignmentRepository
                .findByUserIdAndUnassignedAtIsNullAndDeletedAtIsNull(userId)
                .stream().map(a -> a.getDeviceId()).distinct().toList();
    }

    @Override
    public UUID resolveSeverityId(String severity) {
        if (severity == null || severity.isBlank()) {
            return null;
        }
        String trimmed = severity.trim();
        try {
            return UUID.fromString(trimmed);
        } catch (IllegalArgumentException ignored) {
            String canonical = normalizeSeverity(trimmed);
            SeverityEntity entity = severityRepository
                    .findByCodeAndIsActiveTrue(canonical).orElse(null);
            if (entity == null) {
                throw new InvalidCatalogReferenceException(
                        "severity desconocida: " + severity);
            }
            return entity.getId();
        }
    }

    @Override
    public Page<TimelineRow> findTimeline(UUID deviceId, UUID eventTypeId, UUID severityId,
            OffsetDateTime from, OffsetDateTime to, List<UUID> deviceIds, Pageable pageable) {
        Page<EventEntity> page = eventRepository.findAll(EventSpecifications.filter(
                deviceId, eventTypeId, severityId, from, to, deviceIds), pageable);
        List<TimelineRow> rows = toRows(page.getContent());
        return new PageImpl<>(rows, pageable, page.getTotalElements());
    }

    @Override
    public long countEvents(UUID deviceId, UUID eventTypeId, UUID severityId,
            OffsetDateTime from, OffsetDateTime to, List<UUID> deviceIds) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<EventEntity> root = query.from(EventEntity.class);
        query.select(cb.count(root)).where(filterPredicates(cb, root,
                new QueryFilter(deviceId, eventTypeId, severityId, from, to, deviceIds, false)));
        return entityManager.createQuery(query).getSingleResult();
    }

    @Override
    public long countDistinctDevices(UUID deviceId, UUID eventTypeId, UUID severityId,
            OffsetDateTime from, OffsetDateTime to, List<UUID> deviceIds) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<EventEntity> root = query.from(EventEntity.class);
        query.select(cb.countDistinct(root.get("deviceId"))).where(filterPredicates(cb, root,
                new QueryFilter(deviceId, eventTypeId, severityId, from, to, deviceIds, false)));
        return entityManager.createQuery(query).getSingleResult();
    }

    @Override
    public List<TypeCount> countByType(UUID deviceId, UUID eventTypeId, UUID severityId,
            OffsetDateTime from, OffsetDateTime to, List<UUID> deviceIds) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Object[]> query = cb.createQuery(Object[].class);
        Root<EventEntity> root = query.from(EventEntity.class);
        query.multiselect(root.get("eventTypeId"), cb.count(root))
                .where(filterPredicates(cb, root,
                        new QueryFilter(deviceId, eventTypeId, severityId, from, to, deviceIds, false)))
                .groupBy(root.get("eventTypeId"));
        List<TypeCount> result = new ArrayList<>();
        for (Object[] row : entityManager.createQuery(query).getResultList()) {
            result.add(new TypeCount((UUID) row[0], (Long) row[1]));
        }
        return result;
    }

    @Override
    public List<SeverityCount> countBySeverity(UUID deviceId, UUID eventTypeId, UUID severityId,
            OffsetDateTime from, OffsetDateTime to, List<UUID> deviceIds) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Object[]> query = cb.createQuery(Object[].class);
        Root<EventEntity> root = query.from(EventEntity.class);
        query.multiselect(root.get("severityId"), cb.count(root))
                .where(filterPredicates(cb, root,
                        new QueryFilter(deviceId, eventTypeId, severityId, from, to, deviceIds, false)))
                .groupBy(root.get("severityId"));
        List<SeverityCount> result = new ArrayList<>();
        for (Object[] row : entityManager.createQuery(query).getResultList()) {
            result.add(new SeverityCount((UUID) row[0], (Long) row[1]));
        }
        return result;
    }

    @Override
    public long countOfflineSync(UUID deviceId, UUID eventTypeId, UUID severityId,
            OffsetDateTime from, OffsetDateTime to, List<UUID> deviceIds) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<EventEntity> root = query.from(EventEntity.class);
        query.select(cb.count(root)).where(filterPredicates(cb, root,
                new QueryFilter(deviceId, eventTypeId, severityId, from, to, deviceIds, true)));
        return entityManager.createQuery(query).getSingleResult();
    }

    @Override
    public Optional<OffsetDateTime> findEarliestOccurredAt(UUID deviceId, UUID eventTypeId,
            UUID severityId, OffsetDateTime from, OffsetDateTime to, List<UUID> deviceIds) {
        return findBoundaryOccurredAt(new QueryFilter(deviceId, eventTypeId, severityId,
                from, to, deviceIds, false), true);
    }

    @Override
    public Optional<OffsetDateTime> findLatestOccurredAt(UUID deviceId, UUID eventTypeId,
            UUID severityId, OffsetDateTime from, OffsetDateTime to, List<UUID> deviceIds) {
        return findBoundaryOccurredAt(new QueryFilter(deviceId, eventTypeId, severityId,
                from, to, deviceIds, false), false);
    }

    @Override
    public List<OffsetDateTime> findOccurredAts(UUID deviceId, UUID eventTypeId, UUID severityId,
            OffsetDateTime from, OffsetDateTime to, List<UUID> deviceIds) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<OffsetDateTime> query = cb.createQuery(OffsetDateTime.class);
        Root<EventEntity> root = query.from(EventEntity.class);
        query.select(root.get("occurredAt"))
                .where(filterPredicates(cb, root,
                        new QueryFilter(deviceId, eventTypeId, severityId, from, to, deviceIds, false)))
                .orderBy(cb.asc(root.get("occurredAt")));
        return entityManager.createQuery(query).getResultList();
    }

    @Override
    public Map<UUID, TypeView> findTypeViews(Set<UUID> eventTypeIds) {
        Map<UUID, TypeView> views = new HashMap<>();
        if (eventTypeIds == null || eventTypeIds.isEmpty()) {
            return views;
        }
        for (EventTypeEntity type : eventTypeRepository.findAllById(eventTypeIds)) {
            views.put(type.getId(), new TypeView(type.getId(), type.getCode(),
                    type.getName(), type.getEventCategoryId()));
        }
        return views;
    }

    @Override
    public Map<UUID, SeverityView> findSeverityViews(Set<UUID> severityIds) {
        Map<UUID, SeverityView> views = new HashMap<>();
        if (severityIds == null || severityIds.isEmpty()) {
            return views;
        }
        for (SeverityEntity severity : severityRepository.findAllById(severityIds)) {
            views.put(severity.getId(), new SeverityView(severity.getId(),
                    severity.getCode(), severity.getName(),
                    severity.getPriority() == null ? 0 : severity.getPriority()));
        }
        return views;
    }

    @Override
    public Map<UUID, CategoryView> findCategoryViews(Set<UUID> eventCategoryIds) {
        Map<UUID, CategoryView> views = new HashMap<>();
        if (eventCategoryIds == null || eventCategoryIds.isEmpty()) {
            return views;
        }
        eventCategoryRepository.findAllById(eventCategoryIds).forEach(category ->
                views.put(category.getId(), new CategoryView(category.getId(),
                        category.getCode(), category.getName())));
        return views;
    }

    private Optional<OffsetDateTime> findBoundaryOccurredAt(QueryFilter filter, boolean earliest) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<OffsetDateTime> query = cb.createQuery(OffsetDateTime.class);
        Root<EventEntity> root = query.from(EventEntity.class);
        query.select(root.get("occurredAt")).where(filterPredicates(cb, root, filter));
        if (earliest) {
            query.orderBy(cb.asc(root.get("occurredAt")));
        } else {
            query.orderBy(cb.desc(root.get("occurredAt")));
        }
        List<OffsetDateTime> rows =
                entityManager.createQuery(query).setMaxResults(1).getResultList();
        if (rows.isEmpty() || rows.get(0) == null) {
            return Optional.empty();
        }
        return Optional.of(rows.get(0));
    }

    private Predicate[] filterPredicates(CriteriaBuilder cb, Root<EventEntity> root,
            QueryFilter filter) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.isNull(root.get("deletedAt")));
        if (filter.deviceId() != null) {
            predicates.add(cb.equal(root.get("deviceId"), filter.deviceId()));
        } else if (filter.deviceIds() != null) {
            if (filter.deviceIds().isEmpty()) {
                predicates.add(cb.disjunction());
            } else {
                predicates.add(root.get("deviceId").in(filter.deviceIds()));
            }
        }
        if (filter.eventTypeId() != null) {
            predicates.add(cb.equal(root.get("eventTypeId"), filter.eventTypeId()));
        }
        if (filter.severityId() != null) {
            predicates.add(cb.equal(root.get("severityId"), filter.severityId()));
        }
        if (filter.from() != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), filter.from()));
        }
        if (filter.to() != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("occurredAt"), filter.to()));
        }
        if (filter.offlineOnly()) {
            predicates.add(cb.isTrue(root.get("isOfflineSync")));
        }
        return predicates.toArray(new Predicate[0]);
    }

    private record QueryFilter(UUID deviceId, UUID eventTypeId, UUID severityId,
            OffsetDateTime from, OffsetDateTime to, List<UUID> deviceIds, boolean offlineOnly) {
    }

    private List<TimelineRow> toRows(List<EventEntity> events) {
        if (events.isEmpty()) {
            return List.of();
        }
        List<UUID> eventIds = events.stream().map(EventEntity::getId).toList();
        Map<UUID, EvidenceEntity> evidences = new HashMap<>();
        for (EvidenceEntity evidence : evidenceRepository.findByEventIdIn(eventIds)) {
            evidences.put(evidence.getEventId(), evidence);
        }
        List<UUID> mediaIds = evidences.values().stream()
                .map(EvidenceEntity::getMediaTypeId).distinct().toList();
        Map<UUID, MediaTypeEntity> mediaTypes = new HashMap<>();
        if (!mediaIds.isEmpty()) {
            mediaTypeRepository.findAllById(mediaIds)
                    .forEach(media -> mediaTypes.put(media.getId(), media));
        }
        List<TimelineRow> rows = new ArrayList<>(events.size());
        for (EventEntity event : events) {
            EvidenceEntity evidence = evidences.get(event.getId());
            MediaTypeEntity media =
                    evidence == null ? null : mediaTypes.get(evidence.getMediaTypeId());
            rows.add(new TimelineRow(event.getId(), event.getDeviceId(),
                    event.getEventTypeId(), event.getSeverityId(),
                    event.getSoundPatternId(), event.getOccurredAt(),
                    event.getIsOfflineSync(), event.getMetadata(), event.getCreatedAt(),
                    evidence != null,
                    evidence == null ? null : evidence.getMediaTypeId(),
                    media == null ? null : media.getCode(),
                    media == null ? null : media.getName(),
                    media == null ? null : media.getMimeType()));
        }
        return rows;
    }

    /**
     * Normalización canónica de severidades ES/EN.
     * Duplica {@code TelemetryService.normalizeSeverity} (no visible fuera de su
     * paquete) para resolver códigos en consultas sin acoplarse a la ingesta.
     */
    static String normalizeSeverity(String raw) {
        String upper = raw.trim().toUpperCase();
        switch (upper) {
            case "INFO":
            case "LEVE":
                return "info";
            case "WARNING":
            case "MODERADA":
                return "warning";
            case "HIGH":
            case "SEVERA":
                return "high";
            case "CRITICAL":
            case "CRITICA":
                return "critical";
            default:
                return raw.trim().toLowerCase();
        }
    }
}
