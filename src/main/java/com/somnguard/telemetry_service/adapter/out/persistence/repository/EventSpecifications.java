package com.somnguard.telemetry_service.adapter.out.persistence.repository;

import com.somnguard.telemetry_service.adapter.out.persistence.entity.EventEntity;
import jakarta.persistence.criteria.Predicate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * Filtros HU-API-008 AC-001 (FEA-TEL-QUERY).
 * Orden canónico {@code occurred_at DESC} apoyado en
 * {@code idx_event_device_time(device_id, occurred_at DESC)} (AC-004).
 */
public final class EventSpecifications {

    private EventSpecifications() {}

    /**
     * @param deviceId filtro exacto (nullable)
     * @param eventTypeId filtro exacto (nullable)
     * @param severityId filtro exacto ya resuelto a UUID (nullable)
     * @param from {@code occurred_at >= from} (nullable)
     * @param to {@code occurred_at <= to} (nullable)
     * @param deviceIds alcance ownership para no-admin (nullable; vacío = sin filas)
     */
    public static Specification<EventEntity> filter(UUID deviceId, UUID eventTypeId,
            UUID severityId, OffsetDateTime from, OffsetDateTime to, List<UUID> deviceIds) {
        return (root, query, criteria) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteria.isNull(root.get("deletedAt")));
            if (deviceId != null) {
                predicates.add(criteria.equal(root.get("deviceId"), deviceId));
            } else if (deviceIds != null) {
                if (deviceIds.isEmpty()) {
                    predicates.add(criteria.disjunction());
                } else {
                    predicates.add(root.get("deviceId").in(deviceIds));
                }
            }
            if (eventTypeId != null) {
                predicates.add(criteria.equal(root.get("eventTypeId"), eventTypeId));
            }
            if (severityId != null) {
                predicates.add(criteria.equal(root.get("severityId"), severityId));
            }
            if (from != null) {
                predicates.add(criteria.greaterThanOrEqualTo(root.get("occurredAt"), from));
            }
            if (to != null) {
                predicates.add(criteria.lessThanOrEqualTo(root.get("occurredAt"), to));
            }
            return criteria.and(predicates.toArray(new Predicate[0]));
        };
    }
}
