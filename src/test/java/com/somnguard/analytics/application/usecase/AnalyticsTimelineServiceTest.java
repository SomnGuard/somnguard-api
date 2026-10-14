package com.somnguard.analytics.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.somnguard.analytics.adapter.in.web.dto.AnalyticsTimelineResponse;
import com.somnguard.analytics.application.port.out.AnalyticsEventQueryPort;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class AnalyticsTimelineServiceTest {

    @Mock
    AnalyticsEventQueryPort queries;

    AnalyticsTimelineService service;

    final UUID userId = UUID.randomUUID();
    final UUID deviceId = UUID.randomUUID();
    final UUID eventTypeId = UUID.randomUUID();
    final UUID severityId = UUID.randomUUID();
    final UUID categoryId = UUID.randomUUID();
    final UUID eventId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AnalyticsTimelineService(queries);
    }

    private AnalyticsEventQueryPort.TimelineRow row() {
        return new AnalyticsEventQueryPort.TimelineRow(eventId, deviceId, eventTypeId,
                severityId, UUID.randomUUID(), OffsetDateTime.parse("2026-03-15T10:00:00Z"),
                false, Map.of("message", "bostezo"),
                OffsetDateTime.parse("2026-03-15T10:00:01Z"), true, UUID.randomUUID(),
                "image_jpeg", "Imagen JPEG", "image/jpeg");
    }

    private void stubViews() {
        when(queries.findTypeViews(any(Set.class))).thenReturn(Map.of(eventTypeId,
                new AnalyticsEventQueryPort.TypeView(eventTypeId, "EV-SOM-02",
                        "Somnolencia moderada", categoryId)));
        when(queries.findSeverityViews(any(Set.class))).thenReturn(Map.of(severityId,
                new AnalyticsEventQueryPort.SeverityView(severityId, "warning",
                        "Moderada", (short) 2)));
        when(queries.findCategoryViews(any(Set.class))).thenReturn(Map.of(categoryId,
                new AnalyticsEventQueryPort.CategoryView(categoryId, "SOM", "Somnolencia")));
    }

    private void stubAdminPage() {
        Pageable pageable = PageRequest.of(0, 20,
                Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.asc("id")));
        when(queries.findTimeline(eq(null), eq(null), eq(null), any(), any(), eq(null), any()))
                .thenReturn(new PageImpl<>(List.of(row()), pageable, 1));
    }

    @Test
    void timelineEnrichesAndPaginatesForAdmin() {
        stubViews();
        stubAdminPage();
        AnalyticsFilter filter = new AnalyticsFilter(null, null, null,
                OffsetDateTime.parse("2026-03-01T00:00:00Z"),
                OffsetDateTime.parse("2026-03-31T00:00:00Z"));
        AnalyticsTimelineResponse response = service.timeline(filter, null, true, 1, 20);
        assertEquals(1, response.data().size());
        assertEquals("EV-SOM-02", response.data().get(0).eventType().code());
        assertEquals("SOM", response.data().get(0).eventCategory().code());
        assertEquals("warning", response.data().get(0).severity().code());
        assertTrue(response.data().get(0).hasEvidence());
        assertEquals(1, response.pagination().page());
        assertEquals(20, response.pagination().pageSize());
        assertEquals(1L, response.pagination().totalItems());
        assertNotNull(response.data().get(0).metadata());
    }

    @Test
    void timelineClampsPageAndSize() {
        Pageable pageable = PageRequest.of(0, 100,
                Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.asc("id")));
        when(queries.findTimeline(eq(null), eq(null), eq(null), any(), any(), eq(null), any()))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));
        AnalyticsTimelineResponse response =
                service.timeline(new AnalyticsFilter(null, null, null, null, null),
                        null, true, 0, 500);
        assertEquals(1, response.pagination().page());
        assertEquals(100, response.pagination().pageSize());
        assertTrue(response.data().isEmpty());
    }

    @Test
    void timelineRejectsInvertedRange() {
        AnalyticsFilter filter = new AnalyticsFilter(null, null, null,
                OffsetDateTime.parse("2026-04-01T00:00:00Z"),
                OffsetDateTime.parse("2026-03-01T00:00:00Z"));
        assertThrows(IllegalArgumentException.class,
                () -> service.timeline(filter, null, true, 1, 20));
    }

    @Test
    void timelineReturnsEmptyWhenUserOwnsNothing() {
        when(queries.findOwnedDeviceIds(userId)).thenReturn(List.of());
        AnalyticsTimelineResponse response = service.timeline(
                new AnalyticsFilter(null, null, null, null, null), userId, false, 1, 20);
        assertTrue(response.data().isEmpty());
        assertEquals(0L, response.pagination().totalItems());
    }

    @Test
    void timelineReturnsEmptyForForeignDevice() {
        when(queries.findOwnedDeviceIds(userId)).thenReturn(List.of(UUID.randomUUID()));
        AnalyticsTimelineResponse response = service.timeline(
                new AnalyticsFilter(deviceId, null, null, null, null), userId, false, 1, 20);
        assertTrue(response.data().isEmpty());
    }

    @Test
    void timelineScopesToOwnedDevices() {
        stubViews();
        Pageable pageable = PageRequest.of(0, 20,
                Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.asc("id")));
        when(queries.findOwnedDeviceIds(userId)).thenReturn(List.of(deviceId));
        when(queries.findTimeline(eq(null), eq(null), eq(null), any(), any(),
                eq(List.of(deviceId)), any()))
                .thenReturn(new PageImpl<>(List.of(row()), pageable, 1));
        AnalyticsTimelineResponse response = service.timeline(
                new AnalyticsFilter(null, null, null, null, null), userId, false, 1, 20);
        assertEquals(1, response.data().size());
        verify(queries).findOwnedDeviceIds(userId);
    }

    @Test
    void timelineWithoutEvidenceLeavesMediaNull() {
        AnalyticsEventQueryPort.TimelineRow withoutEvidence = new AnalyticsEventQueryPort
                .TimelineRow(eventId, deviceId, eventTypeId, severityId, null,
                        OffsetDateTime.parse("2026-03-15T10:00:00Z"), false, Map.of(),
                        OffsetDateTime.parse("2026-03-15T10:00:01Z"), false, null,
                        null, null, null);
        stubViews();
        Pageable pageable = PageRequest.of(0, 20,
                Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.asc("id")));
        when(queries.findTimeline(eq(null), eq(null), eq(null), any(), any(), eq(null), any()))
                .thenReturn(new PageImpl<>(List.of(withoutEvidence), pageable, 1));
        AnalyticsTimelineResponse response = service.timeline(
                new AnalyticsFilter(null, null, null, null, null), null, true, 1, 20);
        assertEquals(Boolean.FALSE, response.data().get(0).hasEvidence());
        assertNull(response.data().get(0).mediaType());
    }
}
