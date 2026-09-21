package com.somnguard.telemetry_service.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.somnguard.device_management.adapter.out.persistence.entity.DeviceAssignmentEntity;
import com.somnguard.device_management.adapter.out.persistence.repository.DeviceAssignmentRepository;
import com.somnguard.parameterization.adapter.out.persistence.entity.EventTypeEntity;
import com.somnguard.parameterization.adapter.out.persistence.entity.MediaTypeEntity;
import com.somnguard.parameterization.adapter.out.persistence.entity.SeverityEntity;
import com.somnguard.parameterization.adapter.out.persistence.repository.EventTypeRepository;
import com.somnguard.parameterization.adapter.out.persistence.repository.MediaTypeRepository;
import com.somnguard.parameterization.adapter.out.persistence.repository.SeverityRepository;
import com.somnguard.telemetry_service.adapter.in.web.dto.EventPageResponse;
import com.somnguard.telemetry_service.adapter.out.persistence.entity.EventEntity;
import com.somnguard.telemetry_service.adapter.out.persistence.entity.EvidenceEntity;
import com.somnguard.telemetry_service.adapter.out.persistence.repository.EventRepository;
import com.somnguard.telemetry_service.adapter.out.persistence.repository.EvidenceRepository;
import com.somnguard.telemetry_service.domain.exception.InvalidCatalogReferenceException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class EventQueryServiceTest {

    @Mock
    EventRepository eventRepository;
    @Mock
    EventTypeRepository eventTypeRepository;
    @Mock
    SeverityRepository severityRepository;
    @Mock
    EvidenceRepository evidenceRepository;
    @Mock
    MediaTypeRepository mediaTypeRepository;
    @Mock
    DeviceAssignmentRepository assignmentRepository;

    EventQueryService service;

    final UUID deviceId = UUID.randomUUID();
    final UUID eventTypeId = UUID.randomUUID();
    final UUID severityId = UUID.randomUUID();
    final UUID mediaTypeId = UUID.randomUUID();
    final UUID eventId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new EventQueryService(eventRepository, eventTypeRepository,
                severityRepository, evidenceRepository, mediaTypeRepository,
                assignmentRepository);
    }

    private EventEntity event() {
        EventEntity e = new EventEntity();
        e.setId(eventId);
        e.setDeviceId(deviceId);
        e.setEventTypeId(eventTypeId);
        e.setSeverityId(severityId);
        e.setOccurredAt(OffsetDateTime.parse("2026-03-15T10:00:00Z"));
        e.setMetadata(Map.of("message", "bostezo"));
        e.setIsOfflineSync(false);
        e.setCreatedAt(OffsetDateTime.parse("2026-03-15T10:00:01Z"));
        return e;
    }

    @SuppressWarnings("unchecked")
    private void stubCatalogJoin() {
        EventTypeEntity type = new EventTypeEntity();
        type.setId(eventTypeId);
        type.setCode("EV-SOM-02");
        type.setName("Somnolencia moderada");
        SeverityEntity severity = new SeverityEntity();
        severity.setId(severityId);
        severity.setCode("warning");
        severity.setName("Moderada");
        severity.setPriority((short) 2);
        EvidenceEntity evidence = new EvidenceEntity();
        evidence.setId(UUID.randomUUID());
        evidence.setEventId(eventId);
        evidence.setMediaTypeId(mediaTypeId);
        MediaTypeEntity media = new MediaTypeEntity();
        media.setId(mediaTypeId);
        media.setCode("image_jpeg");
        media.setName("Imagen JPEG");
        media.setMimeType("image/jpeg");
        when(eventTypeRepository.findAllById(any())).thenReturn(List.of(type));
        when(severityRepository.findAllById(any())).thenReturn(List.of(severity));
        when(evidenceRepository.findByEventIdIn(any())).thenReturn(List.of(evidence));
        when(mediaTypeRepository.findAllById(any())).thenReturn(List.of(media));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listAppliesFiltersAndEnrichesJoins() {
        stubCatalogJoin();
        var page = new PageImpl<>(List.of(event()),
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "occurredAt")), 1);
        when(eventRepository.findAll(any(Specification.class),
                any(PageRequest.class))).thenReturn(page);
        EventQueryFilters filters = new EventQueryFilters(deviceId, eventTypeId,
                null, OffsetDateTime.parse("2026-03-01T00:00:00Z"),
                OffsetDateTime.parse("2026-03-31T00:00:00Z"));
        EventPageResponse response = service.list(filters, null, true, 1, 20);
        assertEquals(1, response.data().size());
        assertEquals(eventId, response.data().get(0).id());
        assertEquals("EV-SOM-02", response.data().get(0).eventType().code());
        assertEquals("warning", response.data().get(0).severity().code());
        assertEquals("image_jpeg", response.data().get(0).mediaType().code());
        assertTrue(response.data().get(0).hasEvidence());
        assertEquals(1, response.pagination().page());
        assertEquals(20, response.pagination().pageSize());
        assertEquals(1, response.pagination().totalItems());
        assertEquals(1, response.pagination().totalPages());
    }

    @Test
    @SuppressWarnings("unchecked")
    void listResolvesSeverityCode() {
        SeverityEntity severity = new SeverityEntity();
        severity.setId(severityId);
        severity.setCode("critical");
        when(severityRepository.findByCodeAndIsActiveTrue("critical"))
                .thenReturn(Optional.of(severity));
        when(eventTypeRepository.findAllById(any())).thenReturn(List.of());
        when(severityRepository.findAllById(any())).thenReturn(List.of(severity));
        when(evidenceRepository.findByEventIdIn(any())).thenReturn(List.of());
        when(mediaTypeRepository.findAllById(any())).thenReturn(List.of());
        var page = new PageImpl<>(List.of(event()),
                PageRequest.of(0, 20), 1);
        when(eventRepository.findAll(any(Specification.class),
                any(PageRequest.class))).thenReturn(page);
        EventQueryFilters filters = new EventQueryFilters(null, null,
                "CRITICA", null, null);
        EventPageResponse response = service.list(filters, null, true, 1, 20);
        assertNotNull(response);
        verify(severityRepository).findByCodeAndIsActiveTrue("critical");
    }

    @Test
    void listUnknownSeverityThrows422() {
        when(severityRepository.findByCodeAndIsActiveTrue("nope"))
                .thenReturn(Optional.empty());
        EventQueryFilters filters = new EventQueryFilters(null, null, "nope", null, null);
        assertThrows(InvalidCatalogReferenceException.class,
                () -> service.list(filters, null, true, 1, 20));
        verify(eventRepository, never()).findAll(any(Specification.class),
                any(PageRequest.class));
    }

    @Test
    void listFromAfterToThrows400() {
        EventQueryFilters filters = new EventQueryFilters(null, null, null,
                OffsetDateTime.parse("2026-04-01T00:00:00Z"),
                OffsetDateTime.parse("2026-03-01T00:00:00Z"));
        assertThrows(IllegalArgumentException.class,
                () -> service.list(filters, null, true, 1, 20));
    }

    @Test
    void listNonAdminWithoutOwnershipReturnsEmpty() {
        UUID user = UUID.randomUUID();
        when(assignmentRepository.findByUserIdAndUnassignedAtIsNullAndDeletedAtIsNull(user))
                .thenReturn(List.of());
        EventPageResponse response = service.list(
                new EventQueryFilters(null, null, null, null, null), user, false, 1, 20);
        assertTrue(response.data().isEmpty());
        assertEquals(0, response.pagination().totalItems());
        verify(eventRepository, never()).findAll(any(Specification.class),
                any(PageRequest.class));
    }

    @Test
    void listNonAdminForeignDeviceReturnsEmpty() {
        UUID user = UUID.randomUUID();
        UUID otherDevice = UUID.randomUUID();
        DeviceAssignmentEntity assignment = new DeviceAssignmentEntity();
        assignment.setDeviceId(otherDevice);
        assignment.setUserId(user);
        when(assignmentRepository.findByUserIdAndUnassignedAtIsNullAndDeletedAtIsNull(user))
                .thenReturn(List.of(assignment));
        EventPageResponse response = service.list(
                new EventQueryFilters(deviceId, null, null, null, null), user, false, 1, 20);
        assertTrue(response.data().isEmpty());
        verify(eventRepository, never()).findAll(any(Specification.class),
                any(PageRequest.class));
    }
}
