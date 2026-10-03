package com.somnguard.monitoring.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.somnguard.device_management.adapter.out.persistence.entity.DeviceAssignmentEntity;
import com.somnguard.device_management.adapter.out.persistence.repository.DeviceAssignmentRepository;
import com.somnguard.monitoring.adapter.out.persistence.entity.DeviceTokenEntity;
import com.somnguard.monitoring.adapter.out.persistence.entity.NotificationEntity;
import com.somnguard.monitoring.adapter.out.persistence.entity.NotificationTemplateEntity;
import com.somnguard.monitoring.adapter.out.persistence.entity.UserNotificationPreferenceEntity;
import com.somnguard.monitoring.adapter.out.persistence.repository.DeviceTokenRepository;
import com.somnguard.monitoring.adapter.out.persistence.repository.NotificationRepository;
import com.somnguard.monitoring.adapter.out.persistence.repository.NotificationStatusAuditRepository;
import com.somnguard.monitoring.adapter.out.persistence.repository.NotificationTemplateRepository;
import com.somnguard.monitoring.adapter.out.persistence.repository.UserNotificationPreferenceRepository;
import com.somnguard.monitoring.application.port.out.PushSender;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    NotificationRepository notificationRepository;
    @Mock
    NotificationTemplateRepository templateRepository;
    @Mock
    DeviceTokenRepository deviceTokenRepository;
    @Mock
    UserNotificationPreferenceRepository preferenceRepository;
    @Mock
    NotificationStatusAuditRepository auditRepository;
    @Mock
    DeviceAssignmentRepository assignmentRepository;
    @Mock
    PushSender pushSender;

    NotificationService service;

    final UUID deviceId = UUID.randomUUID();
    final UUID userId = UUID.randomUUID();
    final UUID alertId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new NotificationService(notificationRepository, templateRepository,
                deviceTokenRepository, preferenceRepository, auditRepository,
                assignmentRepository, pushSender,
                Set.of("EV-SOM-05", "EV-DIS-02", "EV-DIS-04", "EV-CIN-01", "EV-CIN-02"), 3);
    }

    private DeviceAssignmentEntity assignment() {
        DeviceAssignmentEntity assignment = new DeviceAssignmentEntity();
        assignment.setId(UUID.randomUUID());
        assignment.setDeviceId(deviceId);
        assignment.setUserId(userId);
        return assignment;
    }

    private UserNotificationPreferenceEntity prefs() {
        UserNotificationPreferenceEntity prefs = new UserNotificationPreferenceEntity();
        prefs.setUserId(userId);
        prefs.setPushEnabled(true);
        prefs.setEmailEnabled(false);
        prefs.setInAppEnabled(true);
        prefs.setMinSeverityCode("critical");
        prefs.setTimezone("America/Bogota");
        return prefs;
    }

    private DeviceTokenEntity token() {
        DeviceTokenEntity token = new DeviceTokenEntity();
        token.setId(UUID.randomUUID());
        token.setUserId(userId);
        token.setToken("fcm-token-123456");
        token.setPlatform("fcm");
        token.setIsActive(true);
        return token;
    }

    private void stubOwnerAndPrefs() {
        when(assignmentRepository.findByDeviceIdAndUnassignedAtIsNullAndDeletedAtIsNull(deviceId))
                .thenReturn(Optional.of(assignment()));
        when(preferenceRepository.findById(userId)).thenReturn(Optional.of(prefs()));
    }

    @Test
    void triggerCriticalRoutesSinglePushWhenAppToken() {
        stubOwnerAndPrefs();
        when(deviceTokenRepository.findByUserIdAndDeletedAtIsNullAndIsActiveTrue(userId))
                .thenReturn(List.of(token()));
        when(pushSender.send(any(), any(), any(), any()))
                .thenReturn(new PushSender.PushResult(true, "msg-1", null));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<NotificationEntity> created = service.triggerCriticalEvent(
                alertId, deviceId, "EV-SOM-05", "critical", userId);

        // Entrega única: con app (token) va solo push, no par push+in_app.
        assertEquals(1, created.size());
        assertEquals("push", created.get(0).getChannel());
        verify(pushSender).send(any(), any(), any(), any());
        verify(auditRepository, org.mockito.Mockito.times(2)).save(any());
    }

    @Test
    void triggerWithoutAppTokenFallsBackToSingleInApp() {
        stubOwnerAndPrefs();
        when(deviceTokenRepository.findByUserIdAndDeletedAtIsNullAndIsActiveTrue(userId))
                .thenReturn(List.of());
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<NotificationEntity> created = service.triggerCriticalEvent(
                alertId, deviceId, "EV-SOM-05", "critical", userId);

        // Solo portal: una sola in_app, sin push diferida.
        assertEquals(1, created.size());
        assertEquals("in_app", created.get(0).getChannel());
        verify(pushSender, never()).send(any(), any(), any(), any());
    }

    @Test
    void triggerWithAllChannelsOffCreatesNothing() {
        when(assignmentRepository.findByDeviceIdAndUnassignedAtIsNullAndDeletedAtIsNull(deviceId))
                .thenReturn(Optional.of(assignment()));
        UserNotificationPreferenceEntity off = prefs();
        off.setPushEnabled(false);
        off.setInAppEnabled(false);
        when(preferenceRepository.findById(userId)).thenReturn(Optional.of(off));

        List<NotificationEntity> created = service.triggerCriticalEvent(
                alertId, deviceId, "EV-SOM-05", "critical", userId);

        assertTrue(created.isEmpty());
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void triggerNonCriticalIsIgnored() {
        List<NotificationEntity> created = service.triggerCriticalEvent(
                alertId, deviceId, "EV-SOM-01", "warning", userId);

        assertTrue(created.isEmpty());
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void triggerCriticalEventTypeWithNonCriticalSeverityStillNotifies() {
        when(assignmentRepository.findByDeviceIdAndUnassignedAtIsNullAndDeletedAtIsNull(deviceId))
                .thenReturn(Optional.of(assignment()));
        UserNotificationPreferenceEntity relaxed = prefs();
        relaxed.setMinSeverityCode("warning");
        when(preferenceRepository.findById(userId)).thenReturn(Optional.of(relaxed));
        when(deviceTokenRepository.findByUserIdAndDeletedAtIsNullAndIsActiveTrue(userId))
                .thenReturn(List.of(token()));
        when(pushSender.send(any(), any(), any(), any()))
                .thenReturn(new PushSender.PushResult(true, "msg-1", null));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // EV-DIS-02 llega del device como MODERADA/warning: el AC-001 lo lista igual.
        List<NotificationEntity> created = service.triggerCriticalEvent(
                alertId, deviceId, "EV-DIS-02", "warning", userId);

        assertEquals(1, created.size());
        assertEquals("push", created.get(0).getChannel());
    }

    @Test
    void triggerWithoutOwnerIsIgnored() {
        when(assignmentRepository.findByDeviceIdAndUnassignedAtIsNullAndDeletedAtIsNull(deviceId))
                .thenReturn(Optional.empty());

        List<NotificationEntity> created = service.triggerCriticalEvent(
                alertId, deviceId, "EV-SOM-05", "critical", userId);

        assertTrue(created.isEmpty());
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void triggerRespectsMinSeverity() {
        when(assignmentRepository.findByDeviceIdAndUnassignedAtIsNullAndDeletedAtIsNull(deviceId))
                .thenReturn(Optional.of(assignment()));
        UserNotificationPreferenceEntity strict = prefs();
        strict.setMinSeverityCode("critical");
        when(preferenceRepository.findById(userId)).thenReturn(Optional.of(strict));

        // EV-DIS-02 pasa el trigger por event_type pero se filtra por severidad mínima.
        List<NotificationEntity> created = service.triggerCriticalEvent(
                alertId, deviceId, "EV-DIS-02", "info", userId);

        assertTrue(created.isEmpty());
    }

    @Test
    void triggerWithoutPushSendsOnlyInApp() {
        stubOwnerAndPrefs();
        UserNotificationPreferenceEntity noPush = prefs();
        noPush.setPushEnabled(false);
        when(preferenceRepository.findById(userId)).thenReturn(Optional.of(noPush));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<NotificationEntity> created = service.triggerCriticalEvent(
                alertId, deviceId, "EV-SOM-05", "critical", userId);

        assertEquals(1, created.size());
        assertEquals("in_app", created.get(0).getChannel());
        verify(pushSender, never()).send(any(), any(), any(), any());
    }

    @Test
    void markReadSetsTrackingTimestamps() {
        UUID notificationId = UUID.randomUUID();
        NotificationEntity entity = new NotificationEntity();
        entity.setId(notificationId);
        entity.setUserId(userId);
        entity.setStatus("NOTIFICATION_SENT");
        entity.setStatusCategory("ACTIVE");
        when(notificationRepository.findById(notificationId))
                .thenReturn(Optional.of(entity));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NotificationEntity read = service.markRead(notificationId, userId);

        assertEquals("NOTIFICATION_READ", read.getStatus());
        assertTrue(read.getReadAt() != null);
        assertTrue(read.getDeliveredAt() != null);
    }

    @Test
    void retryPendingResendsFailedPush() {
        NotificationEntity failed = new NotificationEntity();
        failed.setId(UUID.randomUUID());
        failed.setUserId(userId);
        failed.setTitle("t");
        failed.setMessage("m");
        failed.setChannel("push");
        failed.setStatus("NOTIFICATION_FAILED");
        failed.setStatusCategory("ERROR");
        failed.setRetryCount((short) 1);
        failed.setNextRetryAt(OffsetDateTime.now().minusMinutes(5));
        when(notificationRepository.findByStatusAndNextRetryAtBeforeAndDeletedAtIsNull(
                any(), any())).thenReturn(List.of(failed));
        when(deviceTokenRepository.findByUserIdAndDeletedAtIsNullAndIsActiveTrue(userId))
                .thenReturn(List.of(token()));
        when(pushSender.send(any(), any(), any(), any()))
                .thenReturn(new PushSender.PushResult(true, "msg-2", null));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int processed = service.retryPending(OffsetDateTime.now());

        assertEquals(1, processed);
        ArgumentCaptor<NotificationEntity> captor =
                ArgumentCaptor.forClass(NotificationEntity.class);
        verify(notificationRepository).save(captor.capture());
        assertEquals("NOTIFICATION_SENT", captor.getValue().getStatus());
    }

    @Test
    void registerTokenRejectsBadPlatform() {
        assertThrows(IllegalArgumentException.class,
                () -> service.registerToken(userId, "tok", "sms", null, null));
    }
}
