package com.somnguard.telemetry_service.application.usecase;

import com.somnguard.device_management.adapter.out.persistence.repository.DeviceAssignmentRepository;
import com.somnguard.parameterization.adapter.out.persistence.entity.EventTypeEntity;
import com.somnguard.parameterization.adapter.out.persistence.entity.MediaTypeEntity;
import com.somnguard.parameterization.adapter.out.persistence.entity.SeverityEntity;
import com.somnguard.parameterization.adapter.out.persistence.repository.EventTypeRepository;
import com.somnguard.parameterization.adapter.out.persistence.repository.MediaTypeRepository;
import com.somnguard.parameterization.adapter.out.persistence.repository.SeverityRepository;
import com.somnguard.telemetry_service.adapter.in.web.dto.EventDetailDto;
import com.somnguard.telemetry_service.adapter.in.web.dto.EventPageResponse;
import com.somnguard.telemetry_service.adapter.out.persistence.entity.EventEntity;
import com.somnguard.telemetry_service.adapter.out.persistence.entity.EvidenceEntity;
import com.somnguard.telemetry_service.adapter.out.persistence.repository.EventRepository;
import com.somnguard.telemetry_service.adapter.out.persistence.repository.EventSpecifications;
import com.somnguard.telemetry_service.adapter.out.persistence.repository.EvidenceRepository;
import com.somnguard.telemetry_service.domain.exception.InvalidCatalogReferenceException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consulta paginada de eventos (HU-API-008, FEA-TEL-QUERY).
 * Solo lectura: no altera la ingesta HU-API-007.
 */
@Service
public class EventQueryService {

    private final EventRepository eventRepository;
    private final EventTypeRepository eventTypeRepository;
    private final SeverityRepository severityRepository;
    private final EvidenceRepository evidenceRepository;
    private final MediaTypeRepository mediaTypeRepository;
    private final DeviceAssignmentRepository assignmentRepository;

    public EventQueryService(EventRepository eventRepository,
            EventTypeRepository eventTypeRepository,
            SeverityRepository severityRepository,
            EvidenceRepository evidenceRepository,
            MediaTypeRepository mediaTypeRepository,
            DeviceAssignmentRepository assignmentRepository) {
        this.eventRepository = eventRepository;
        this.eventTypeRepository = eventTypeRepository;
        this.severityRepository = severityRepository;
        this.evidenceRepository = evidenceRepository;
        this.mediaTypeRepository = mediaTypeRepository;
        this.assignmentRepository = assignmentRepository;
    }

    @Transactional(readOnly = true)
    public EventPageResponse list(EventQueryFilters filters, UUID scopeUserId,
            boolean adminView, int page, int pageSize) {
        EventQueryFilters safe = filters == null
                ? new EventQueryFilters(null, null, null, null, null) : filters;
        validateRange(safe.from(), safe.to());
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        UUID severityId = resolveSeverityId(safe.severity());
        List<UUID> scopeIds = resolveScopeIds(safe.deviceId(), scopeUserId, adminView);
        if (scopeIds != null && scopeIds.isEmpty()) {
            return emptyPage(safePage, safeSize);
        }
        Pageable pageable = PageRequest.of(safePage - 1, safeSize,
                Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.asc("id")));
        Page<EventEntity> result = eventRepository.findAll(EventSpecifications.filter(
                safe.deviceId(), safe.eventTypeId(), severityId,
                safe.from(), safe.to(), scopeIds), pageable);
        List<EventDetailDto> data = enrich(result.getContent());
        return new EventPageResponse(data, new EventPageResponse.Pagination(
                safePage, safeSize, result.getTotalElements(), result.getTotalPages()));
    }

    private void validateRange(OffsetDateTime from, OffsetDateTime to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("from debe ser anterior o igual a to");
        }
    }

    private EventPageResponse emptyPage(int safePage, int safeSize) {
        return new EventPageResponse(List.of(),
                new EventPageResponse.Pagination(safePage, safeSize, 0, 0));
    }

    private List<UUID> resolveScopeIds(UUID deviceId, UUID scopeUserId, boolean adminView) {
        if (adminView) {
            return null;
        }
        if (scopeUserId == null) {
            return List.of();
        }
        List<UUID> owned = assignmentRepository
                .findByUserIdAndUnassignedAtIsNullAndDeletedAtIsNull(scopeUserId)
                .stream().map(a -> a.getDeviceId()).distinct().toList();
        if (deviceId != null) {
            return owned.contains(deviceId) ? null : List.of();
        }
        return owned;
    }

    UUID resolveSeverityId(String severity) {
        if (severity == null || severity.isBlank()) {
            return null;
        }
        String trimmed = severity.trim();
        try {
            return UUID.fromString(trimmed);
        } catch (IllegalArgumentException ignored) {
            String canonical = TelemetryService.normalizeSeverity(trimmed);
            SeverityEntity entity = severityRepository
                    .findByCodeAndIsActiveTrue(canonical).orElse(null);
            if (entity == null) {
                throw new InvalidCatalogReferenceException(
                        "severity desconocida: " + severity);
            }
            return entity.getId();
        }
    }

    private List<EventDetailDto> enrich(List<EventEntity> events) {
        if (events.isEmpty()) {
            return List.of();
        }
        Map<UUID, EventTypeEntity> types = eventTypeRepository
                .findAllById(collect(events, "type")).stream()
                .collect(Collectors.toMap(EventTypeEntity::getId, e -> e, (a, b) -> a));
        Map<UUID, SeverityEntity> severities = severityRepository
                .findAllById(collect(events, "severity")).stream()
                .collect(Collectors.toMap(SeverityEntity::getId, e -> e, (a, b) -> a));
        List<UUID> eventIds = events.stream().map(EventEntity::getId).toList();
        Map<UUID, EvidenceEntity> evidences = new HashMap<>();
        for (EvidenceEntity ev : evidenceRepository.findByEventIdIn(eventIds)) {
            evidences.put(ev.getEventId(), ev);
        }
        List<UUID> mediaIds = evidences.values().stream()
                .map(EvidenceEntity::getMediaTypeId).distinct().toList();
        Map<UUID, MediaTypeEntity> mediaTypes = mediaTypeRepository
                .findAllById(mediaIds).stream()
                .collect(Collectors.toMap(MediaTypeEntity::getId, e -> e, (a, b) -> a));
        return events.stream().map(e -> toDetail(e, types, severities,
                evidences, mediaTypes)).toList();
    }

    private List<UUID> collect(List<EventEntity> events, String kind) {
        if ("severity".equals(kind)) {
            return events.stream().map(EventEntity::getSeverityId).distinct().toList();
        }
        return events.stream().map(EventEntity::getEventTypeId).distinct().toList();
    }

    private EventDetailDto toDetail(EventEntity event,
            Map<UUID, EventTypeEntity> types, Map<UUID, SeverityEntity> severities,
            Map<UUID, EvidenceEntity> evidences, Map<UUID, MediaTypeEntity> mediaTypes) {
        EventTypeEntity type = types.get(event.getEventTypeId());
        SeverityEntity severity = severities.get(event.getSeverityId());
        EvidenceEntity evidence = evidences.get(event.getId());
        MediaTypeEntity media = evidence == null
                ? null : mediaTypes.get(evidence.getMediaTypeId());
        return new EventDetailDto(event.getId(), event.getDeviceId(),
                type == null ? null : new EventDetailDto.EventTypeRef(
                        type.getId(), type.getCode(), type.getName()),
                severity == null ? null : new EventDetailDto.SeverityRef(
                        severity.getId(), severity.getCode(),
                        severity.getName(), severity.getPriority()),
                event.getOccurredAt(), event.getSoundPatternId(),
                event.getMetadata(), event.getIsOfflineSync(),
                evidence != null,
                media == null ? null : new EventDetailDto.MediaTypeRef(
                        media.getId(), media.getCode(),
                        media.getName(), media.getMimeType()),
                event.getCreatedAt());
    }
}
