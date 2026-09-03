package com.somnguard.parameterization.adapter.in.web;

import com.somnguard.parameterization.adapter.in.web.dto.EventCategoryPatchRequest;
import com.somnguard.parameterization.adapter.in.web.dto.EventCategoryRequest;
import com.somnguard.parameterization.adapter.in.web.dto.EventTypePatchRequest;
import com.somnguard.parameterization.adapter.in.web.dto.EventTypeRequest;
import com.somnguard.parameterization.adapter.in.web.dto.MediaTypePatchRequest;
import com.somnguard.parameterization.adapter.in.web.dto.MediaTypeRequest;
import com.somnguard.parameterization.adapter.in.web.dto.SeverityPatchRequest;
import com.somnguard.parameterization.adapter.in.web.dto.SeverityRequest;
import com.somnguard.parameterization.adapter.in.web.dto.SoundPatternPatchRequest;
import com.somnguard.parameterization.adapter.in.web.dto.SoundPatternRequest;
import com.somnguard.parameterization.adapter.out.persistence.entity.EventCategoryEntity;
import com.somnguard.parameterization.adapter.out.persistence.entity.EventTypeEntity;
import com.somnguard.parameterization.adapter.out.persistence.entity.MediaTypeEntity;
import com.somnguard.parameterization.adapter.out.persistence.entity.SeverityEntity;
import com.somnguard.parameterization.adapter.out.persistence.entity.SoundPatternEntity;
import com.somnguard.parameterization.adapter.out.persistence.repository.EventCategoryRepository;
import com.somnguard.parameterization.adapter.out.persistence.repository.EventTypeRepository;
import com.somnguard.parameterization.adapter.out.persistence.repository.MediaTypeRepository;
import com.somnguard.parameterization.adapter.out.persistence.repository.SeverityRepository;
import com.somnguard.parameterization.adapter.out.persistence.repository.SoundPatternRepository;
import com.somnguard.platform.security.RequireFeature;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/catalogs")
@RequireFeature("catalog.write")
public class ParameterizationController {

    private final EventCategoryRepository eventCategoryRepository;
    private final EventTypeRepository eventTypeRepository;
    private final SeverityRepository severityRepository;
    private final SoundPatternRepository soundPatternRepository;
    private final MediaTypeRepository mediaTypeRepository;

    public ParameterizationController(EventCategoryRepository eventCategoryRepository,
            EventTypeRepository eventTypeRepository, SeverityRepository severityRepository,
            SoundPatternRepository soundPatternRepository, MediaTypeRepository mediaTypeRepository) {
        this.eventCategoryRepository = eventCategoryRepository;
        this.eventTypeRepository = eventTypeRepository;
        this.severityRepository = severityRepository;
        this.soundPatternRepository = soundPatternRepository;
        this.mediaTypeRepository = mediaTypeRepository;
    }

    // event-categories
    @GetMapping("/event-categories")
    @RequireFeature("catalog.read")
    public List<EventCategoryEntity> listEventCategories() {
        return eventCategoryRepository.findByIsActiveTrueOrderBySortOrderAsc();
    }

    @GetMapping("/event-categories/{id}")
    @RequireFeature("catalog.read")
    public EventCategoryEntity getEventCategoryById(@PathVariable UUID id) {
        return eventCategoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("EventCategory not found"));
    }

    @PostMapping("/event-categories")
    public ResponseEntity<EventCategoryEntity> createEventCategory(@Valid @RequestBody EventCategoryRequest req) {
        if (eventCategoryRepository.findByCodeAndIsActiveTrue(req.code()).isPresent()) {
            throw new IllegalStateException("EventCategory code exists");
        }
        EventCategoryEntity e = new EventCategoryEntity();
        e.setId(UUID.randomUUID());
        e.setCode(req.code());
        e.setName(req.name());
        e.setDescription(req.description());
        e.setSortOrder(req.sortOrder() != null ? req.sortOrder() : 0);
        e.setIsActive(true);
        e.setCreatedAt(OffsetDateTime.now());
        e.setCreatedBy(AuditSupport.currentUserId());
        e.setUpdatedAt(OffsetDateTime.now());
        e.setUpdatedBy(AuditSupport.currentUserId());
        return ResponseEntity.status(201).body(eventCategoryRepository.save(e));
    }

    @PatchMapping("/event-categories/{id}")
    public EventCategoryEntity patchEventCategory(@PathVariable UUID id,
            @Valid @RequestBody EventCategoryPatchRequest req) {
        EventCategoryEntity e = eventCategoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("EventCategory not found"));
        if (req.code() != null) {
            var existing = eventCategoryRepository.findByCodeAndIsActiveTrue(req.code())
                    .filter(other -> !other.getId().equals(id));
            if (existing.isPresent()) {
                throw new IllegalStateException("EventCategory code exists");
            }
            e.setCode(req.code());
        }
        if (req.name() != null) {
            e.setName(req.name());
        }
        if (req.description() != null) {
            e.setDescription(req.description());
        }
        if (req.sortOrder() != null) {
            e.setSortOrder(req.sortOrder());
        }
        if (req.isActive() != null) {
            e.setIsActive(req.isActive());
        }
        e.setUpdatedAt(OffsetDateTime.now());
        e.setUpdatedBy(AuditSupport.currentUserId());
        return eventCategoryRepository.save(e);
    }

    @DeleteMapping("/event-categories/{id}")
    public ResponseEntity<Void> deleteEventCategory(@PathVariable UUID id) {
        EventCategoryEntity e = eventCategoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("EventCategory not found"));
        e.setIsActive(false);
        e.setUpdatedAt(OffsetDateTime.now());
        e.setUpdatedBy(AuditSupport.currentUserId());
        eventCategoryRepository.save(e);
        return ResponseEntity.noContent().build();
    }

    // event-types
    @GetMapping("/event-types")
    @RequireFeature("catalog.read")
    public List<EventTypeEntity> listEventTypes() {
        return eventTypeRepository.findByDeletedAtIsNull();
    }

    @GetMapping("/event-types/{id}")
    @RequireFeature("catalog.read")
    public EventTypeEntity getEventTypeById(@PathVariable UUID id) {
        EventTypeEntity e = eventTypeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("EventType not found"));
        if (e.getDeletedAt() != null) {
            throw new IllegalArgumentException("EventType not found");
        }
        return e;
    }

    @PostMapping("/event-types")
    public ResponseEntity<EventTypeEntity> createEventType(@Valid @RequestBody EventTypeRequest req) {
        if (eventTypeRepository.findByCodeAndDeletedAtIsNull(req.code()).isPresent()) {
            throw new IllegalStateException("EventType code exists");
        }
        validateEventTypeReferences(req.eventCategoryId(), req.defaultSeverityId(), req.defaultSoundPatternId());
        EventTypeEntity e = new EventTypeEntity();
        e.setId(UUID.randomUUID());
        e.setCode(req.code());
        e.setName(req.name());
        e.setEventCategoryId(req.eventCategoryId());
        e.setDefaultSeverityId(req.defaultSeverityId());
        e.setDefaultSoundPatternId(req.defaultSoundPatternId());
        e.setThresholdConfig(req.thresholdConfig() != null ? req.thresholdConfig() : Map.of());
        e.setIsActive(true);
        e.setStatus(req.status() != null ? req.status() : "DRAFT");
        e.setStatusCategory(req.statusCategory() != null ? req.statusCategory() : "PENDING");
        e.setCreatedAt(OffsetDateTime.now());
        e.setCreatedBy(AuditSupport.currentUserId());
        e.setUpdatedAt(OffsetDateTime.now());
        e.setUpdatedBy(AuditSupport.currentUserId());
        e.setVersion(1);
        return ResponseEntity.status(201).body(eventTypeRepository.save(e));
    }

    @PatchMapping("/event-types/{id}")
    public EventTypeEntity patchEventType(@PathVariable UUID id, @Valid @RequestBody EventTypePatchRequest req) {
        EventTypeEntity e = eventTypeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("EventType not found"));
        if (e.getDeletedAt() != null) {
            throw new IllegalArgumentException("EventType not found");
        }
        if (req.code() != null) {
            var existing = eventTypeRepository.findByCodeAndDeletedAtIsNull(req.code())
                    .filter(other -> !other.getId().equals(id));
            if (existing.isPresent()) {
                throw new IllegalStateException("EventType code exists");
            }
            e.setCode(req.code());
        }
        if (req.name() != null) {
            e.setName(req.name());
        }
        if (req.eventCategoryId() != null) {
            eventCategoryRepository.findById(req.eventCategoryId())
                    .orElseThrow(() -> new IllegalArgumentException("EventCategory not found"));
            e.setEventCategoryId(req.eventCategoryId());
        }
        if (req.defaultSeverityId() != null) {
            severityRepository.findById(req.defaultSeverityId())
                    .orElseThrow(() -> new IllegalArgumentException("Severity not found"));
            e.setDefaultSeverityId(req.defaultSeverityId());
        }
        if (req.defaultSoundPatternId() != null) {
            soundPatternRepository.findById(req.defaultSoundPatternId())
                    .orElseThrow(() -> new IllegalArgumentException("SoundPattern not found"));
            e.setDefaultSoundPatternId(req.defaultSoundPatternId());
        }
        if (req.thresholdConfig() != null) {
            e.setThresholdConfig(req.thresholdConfig());
        }
        if (req.status() != null) {
            e.setStatus(req.status());
        }
        if (req.statusCategory() != null) {
            e.setStatusCategory(req.statusCategory());
        }
        if (req.isActive() != null) {
            e.setIsActive(req.isActive());
        }
        e.setUpdatedAt(OffsetDateTime.now());
        e.setUpdatedBy(AuditSupport.currentUserId());
        e.setVersion(e.getVersion() + 1);
        return eventTypeRepository.save(e);
    }

    @DeleteMapping("/event-types/{id}")
    public ResponseEntity<Void> deleteEventType(@PathVariable UUID id) {
        EventTypeEntity e = eventTypeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("EventType not found"));
        e.setIsActive(false);
        e.setDeletedAt(OffsetDateTime.now());
        e.setDeletedBy(AuditSupport.currentUserId());
        e.setUpdatedAt(OffsetDateTime.now());
        e.setUpdatedBy(AuditSupport.currentUserId());
        eventTypeRepository.save(e);
        return ResponseEntity.noContent().build();
    }

    // severities
    @GetMapping("/severities")
    @RequireFeature("catalog.read")
    public List<SeverityEntity> listSeverities() {
        return severityRepository.findByIsActiveTrueOrderByPriorityAsc();
    }

    @GetMapping("/severities/{id}")
    @RequireFeature("catalog.read")
    public SeverityEntity getSeverityById(@PathVariable UUID id) {
        return severityRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Severity not found"));
    }

    @PostMapping("/severities")
    public ResponseEntity<SeverityEntity> createSeverity(@Valid @RequestBody SeverityRequest req) {
        if (severityRepository.findByCodeAndIsActiveTrue(req.code()).isPresent()) {
            throw new IllegalStateException("Severity code exists");
        }
        SeverityEntity e = new SeverityEntity();
        e.setId(UUID.randomUUID());
        e.setCode(req.code());
        e.setName(req.name());
        e.setPriority(req.priority() != null ? req.priority() : (short) 1);
        e.setIsActive(true);
        e.setCreatedAt(OffsetDateTime.now());
        e.setCreatedBy(AuditSupport.currentUserId());
        e.setUpdatedAt(OffsetDateTime.now());
        e.setUpdatedBy(AuditSupport.currentUserId());
        return ResponseEntity.status(201).body(severityRepository.save(e));
    }

    @PatchMapping("/severities/{id}")
    public SeverityEntity patchSeverity(@PathVariable UUID id, @Valid @RequestBody SeverityPatchRequest req) {
        SeverityEntity e = severityRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Severity not found"));
        if (req.code() != null) {
            var existing = severityRepository.findByCodeAndIsActiveTrue(req.code())
                    .filter(other -> !other.getId().equals(id));
            if (existing.isPresent()) {
                throw new IllegalStateException("Severity code exists");
            }
            e.setCode(req.code());
        }
        if (req.name() != null) {
            e.setName(req.name());
        }
        if (req.priority() != null) {
            e.setPriority(req.priority());
        }
        if (req.isActive() != null) {
            e.setIsActive(req.isActive());
        }
        e.setUpdatedAt(OffsetDateTime.now());
        e.setUpdatedBy(AuditSupport.currentUserId());
        return severityRepository.save(e);
    }

    @DeleteMapping("/severities/{id}")
    public ResponseEntity<Void> deleteSeverity(@PathVariable UUID id) {
        SeverityEntity e = severityRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Severity not found"));
        e.setIsActive(false);
        e.setUpdatedAt(OffsetDateTime.now());
        e.setUpdatedBy(AuditSupport.currentUserId());
        severityRepository.save(e);
        return ResponseEntity.noContent().build();
    }

    // sound-patterns
    @GetMapping("/sound-patterns")
    @RequireFeature("catalog.read")
    public List<SoundPatternEntity> listSoundPatterns() {
        return soundPatternRepository.findByIsActiveTrue();
    }

    @GetMapping("/sound-patterns/{id}")
    @RequireFeature("catalog.read")
    public SoundPatternEntity getSoundPatternById(@PathVariable UUID id) {
        return soundPatternRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("SoundPattern not found"));
    }

    @PostMapping("/sound-patterns")
    public ResponseEntity<SoundPatternEntity> createSoundPattern(@Valid @RequestBody SoundPatternRequest req) {
        if (soundPatternRepository.findByCodeAndIsActiveTrue(req.code()).isPresent()) {
            throw new IllegalStateException("SoundPattern code exists");
        }
        SoundPatternEntity e = new SoundPatternEntity();
        e.setId(UUID.randomUUID());
        e.setCode(req.code());
        e.setDescription(req.description());
        e.setFrequencyHz(req.frequencyHz());
        e.setDurationMs(req.durationMs());
        e.setRepetitions(req.repetitions() != null ? req.repetitions() : (short) 1);
        e.setPatternType(req.patternType() != null ? req.patternType() : "beep");
        e.setIntervalMs(req.intervalMs());
        e.setIsActive(true);
        e.setCreatedAt(OffsetDateTime.now());
        e.setCreatedBy(AuditSupport.currentUserId());
        e.setUpdatedAt(OffsetDateTime.now());
        e.setUpdatedBy(AuditSupport.currentUserId());
        return ResponseEntity.status(201).body(soundPatternRepository.save(e));
    }

    @PatchMapping("/sound-patterns/{id}")
    public SoundPatternEntity patchSoundPattern(@PathVariable UUID id,
            @Valid @RequestBody SoundPatternPatchRequest req) {
        SoundPatternEntity e = soundPatternRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("SoundPattern not found"));
        if (req.code() != null) {
            var existing = soundPatternRepository.findByCodeAndIsActiveTrue(req.code())
                    .filter(other -> !other.getId().equals(id));
            if (existing.isPresent()) {
                throw new IllegalStateException("SoundPattern code exists");
            }
            e.setCode(req.code());
        }
        if (req.description() != null) {
            e.setDescription(req.description());
        }
        if (req.frequencyHz() != null) {
            e.setFrequencyHz(req.frequencyHz());
        }
        if (req.durationMs() != null) {
            e.setDurationMs(req.durationMs());
        }
        if (req.repetitions() != null) {
            e.setRepetitions(req.repetitions());
        }
        if (req.patternType() != null) {
            e.setPatternType(req.patternType());
        }
        if (req.intervalMs() != null) {
            e.setIntervalMs(req.intervalMs());
        }
        if (req.isActive() != null) {
            e.setIsActive(req.isActive());
        }
        e.setUpdatedAt(OffsetDateTime.now());
        e.setUpdatedBy(AuditSupport.currentUserId());
        return soundPatternRepository.save(e);
    }

    @DeleteMapping("/sound-patterns/{id}")
    public ResponseEntity<Void> deleteSoundPattern(@PathVariable UUID id) {
        SoundPatternEntity e = soundPatternRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("SoundPattern not found"));
        e.setIsActive(false);
        e.setUpdatedAt(OffsetDateTime.now());
        e.setUpdatedBy(AuditSupport.currentUserId());
        soundPatternRepository.save(e);
        return ResponseEntity.noContent().build();
    }

    // media-types
    @GetMapping("/media-types")
    @RequireFeature("catalog.read")
    public List<MediaTypeEntity> listMediaTypes() {
        return mediaTypeRepository.findByIsActiveTrue();
    }

    @GetMapping("/media-types/{id}")
    @RequireFeature("catalog.read")
    public MediaTypeEntity getMediaTypeById(@PathVariable UUID id) {
        return mediaTypeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("MediaType not found"));
    }

    @PostMapping("/media-types")
    public ResponseEntity<MediaTypeEntity> createMediaType(@Valid @RequestBody MediaTypeRequest req) {
        if (mediaTypeRepository.findByCodeAndIsActiveTrue(req.code()).isPresent()) {
            throw new IllegalStateException("MediaType code exists");
        }
        MediaTypeEntity e = new MediaTypeEntity();
        e.setId(UUID.randomUUID());
        e.setCode(req.code());
        e.setName(req.name());
        e.setMimeType(req.mimeType());
        e.setMaxSizeMb(req.maxSizeMb() != null ? req.maxSizeMb() : 10);
        e.setIsActive(true);
        e.setCreatedAt(OffsetDateTime.now());
        e.setCreatedBy(AuditSupport.currentUserId());
        e.setUpdatedAt(OffsetDateTime.now());
        e.setUpdatedBy(AuditSupport.currentUserId());
        return ResponseEntity.status(201).body(mediaTypeRepository.save(e));
    }

    @PatchMapping("/media-types/{id}")
    public MediaTypeEntity patchMediaType(@PathVariable UUID id, @Valid @RequestBody MediaTypePatchRequest req) {
        MediaTypeEntity e = mediaTypeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("MediaType not found"));
        if (req.code() != null) {
            var existing = mediaTypeRepository.findByCodeAndIsActiveTrue(req.code())
                    .filter(other -> !other.getId().equals(id));
            if (existing.isPresent()) {
                throw new IllegalStateException("MediaType code exists");
            }
            e.setCode(req.code());
        }
        if (req.name() != null) {
            e.setName(req.name());
        }
        if (req.mimeType() != null) {
            e.setMimeType(req.mimeType());
        }
        if (req.maxSizeMb() != null) {
            e.setMaxSizeMb(req.maxSizeMb());
        }
        if (req.isActive() != null) {
            e.setIsActive(req.isActive());
        }
        e.setUpdatedAt(OffsetDateTime.now());
        e.setUpdatedBy(AuditSupport.currentUserId());
        return mediaTypeRepository.save(e);
    }

    @DeleteMapping("/media-types/{id}")
    public ResponseEntity<Void> deleteMediaType(@PathVariable UUID id) {
        MediaTypeEntity e = mediaTypeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("MediaType not found"));
        e.setIsActive(false);
        e.setUpdatedAt(OffsetDateTime.now());
        e.setUpdatedBy(AuditSupport.currentUserId());
        mediaTypeRepository.save(e);
        return ResponseEntity.noContent().build();
    }

    private void validateEventTypeReferences(UUID eventCategoryId, UUID severityId, UUID soundPatternId) {
        eventCategoryRepository.findById(eventCategoryId)
                .orElseThrow(() -> new IllegalArgumentException("EventCategory not found"));
        severityRepository.findById(severityId)
                .orElseThrow(() -> new IllegalArgumentException("Severity not found"));
        soundPatternRepository.findById(soundPatternId)
                .orElseThrow(() -> new IllegalArgumentException("SoundPattern not found"));
    }
}
