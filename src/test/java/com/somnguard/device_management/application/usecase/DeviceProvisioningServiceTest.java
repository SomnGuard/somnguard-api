package com.somnguard.device_management.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.somnguard.device_management.adapter.out.persistence.entity.DeviceEntity;
import com.somnguard.device_management.adapter.out.persistence.entity.ProvisioningTokenEntity;
import com.somnguard.device_management.adapter.out.persistence.repository.DeviceAssignmentRepository;
import com.somnguard.device_management.adapter.out.persistence.repository.DeviceRepository;
import com.somnguard.device_management.adapter.out.persistence.repository.DeviceStatusAuditRepository;
import com.somnguard.device_management.adapter.out.persistence.repository.ProvisioningAuditRepository;
import com.somnguard.device_management.adapter.out.persistence.repository.ProvisioningTokenRepository;
import com.somnguard.device_management.application.service.DeviceApiKeyService;
import com.somnguard.device_management.domain.exception.DeviceConflictException;
import com.somnguard.device_management.domain.exception.DeviceNotFoundException;
import com.somnguard.device_management.domain.exception.InvalidDeviceCredentialsException;
import com.somnguard.device_management.domain.model.DeviceStatus;
import com.somnguard.security.adapter.out.persistence.entity.UserEntity;
import com.somnguard.security.adapter.out.persistence.repository.UserRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeviceProvisioningServiceTest {

    @Mock
    DeviceRepository deviceRepository;
    @Mock
    DeviceAssignmentRepository assignmentRepository;
    @Mock
    DeviceStatusAuditRepository auditRepository;
    @Mock
    ProvisioningTokenRepository tokenRepository;
    @Mock
    ProvisioningAuditRepository provisioningAuditRepository;
    @Mock
    UserRepository userRepository;

    DeviceService service;
    final DeviceApiKeyService apiKeyService = new DeviceApiKeyService();
    final UUID adminId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        service = new DeviceService(deviceRepository, assignmentRepository, auditRepository,
                tokenRepository, provisioningAuditRepository, apiKeyService, userRepository);
    }

    @Test
    void createProvisioningTokenStoresOnlyHash() {
        var created = service.createProvisioningToken(null, adminId, "10.0.0.1");

        assertNotNull(created.plainToken());
        assertNotEquals(created.plainToken(), created.token().getTokenHash());
        assertEquals(apiKeyService.hash(created.plainToken()), created.token().getTokenHash());
        assertEquals((short) 1, created.token().getMaxUses());
        assertEquals((short) 0, created.token().getUsesCount());
        assertTrue(created.token().getExpiresAt().isAfter(OffsetDateTime.now().plusDays(6)));
        verify(tokenRepository).save(any());
        verify(provisioningAuditRepository).save(any());
    }

    @Test
    void selfRegisterCreatesDeviceAndConsumesToken() {
        var token = service.createProvisioningToken("SN-001", adminId, "10.0.0.1");
        when(tokenRepository.findByTokenHash(apiKeyService.hash(token.plainToken())))
                .thenReturn(Optional.of(token.token()));
        when(deviceRepository.findBySerialNumberAndDeletedAtIsNull("SN-001"))
                .thenReturn(Optional.empty());

        var result = service.selfRegister("SN-001", "1.0.0", token.plainToken(), "10.0.0.2");

        assertTrue(result.created());
        assertNotNull(result.plainKey());
        assertNotNull(result.plainClaimCode());
        assertEquals(DeviceStatus.DEVICE_REGISTERED.code(), result.device().getStatus());
        assertEquals(token.token().getId(), result.device().getProvisioningTokenId());
        assertEquals((short) 1, token.token().getUsesCount());
        assertEquals(result.device().getId(), token.token().getDeviceId());
    }

    @Test
    void selfRegisterRetryDoesNotReexposeSecrets() {
        var token = service.createProvisioningToken("SN-002", adminId, null);
        DeviceEntity existing = new DeviceEntity();
        existing.setId(UUID.randomUUID());
        existing.setSerialNumber("SN-002");
        existing.setStatus(DeviceStatus.DEVICE_REGISTERED.code());
        existing.setProvisioningTokenId(token.token().getId());
        when(tokenRepository.findByTokenHash(apiKeyService.hash(token.plainToken())))
                .thenReturn(Optional.of(token.token()));
        when(deviceRepository.findBySerialNumberAndDeletedAtIsNull("SN-002"))
                .thenReturn(Optional.of(existing));

        var result = service.selfRegister("SN-002", "1.0.0", token.plainToken(), null);

        assertFalse(result.created());
        assertNull(result.plainKey());
        assertNull(result.plainClaimCode());
        assertEquals(existing.getId(), result.device().getId());
    }

    @Test
    void selfRegisterRejectsUnknownToken() {
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThrows(InvalidDeviceCredentialsException.class,
                () -> service.selfRegister("SN-X", "1.0.0", "token-falso", null));
    }

    @Test
    void selfRegisterRejectsDuplicateSerialFromOtherOrigin() {
        var token = service.createProvisioningToken(null, adminId, null);
        DeviceEntity manual = new DeviceEntity();
        manual.setId(UUID.randomUUID());
        manual.setProvisioningTokenId(null);
        when(tokenRepository.findByTokenHash(apiKeyService.hash(token.plainToken())))
                .thenReturn(Optional.of(token.token()));
        when(deviceRepository.findBySerialNumberAndDeletedAtIsNull("SN-DUP"))
                .thenReturn(Optional.of(manual));

        assertThrows(DeviceConflictException.class,
                () -> service.selfRegister("SN-DUP", "1.0.0", token.plainToken(), null));
    }

    @Test
    void claimAssignsDeviceAndKeepsReusableCode() {
        UUID userId = UUID.randomUUID();
        String claimCode = apiKeyService.generateClaimCode();
        DeviceEntity e = new DeviceEntity();
        e.setId(UUID.randomUUID());
        e.setSerialNumber("SN-003");
        e.setStatus(DeviceStatus.DEVICE_REGISTERED.code());
        e.setStatusCategory(DeviceStatus.DEVICE_REGISTERED.category());
        e.setClaimCode(claimCode);
        e.setProvisioningTokenId(UUID.randomUUID());
        when(deviceRepository.findByClaimCodeAndDeletedAtIsNull(claimCode))
                .thenReturn(Optional.of(e));
        when(assignmentRepository.findByDeviceIdAndUnassignedAtIsNullAndDeletedAtIsNull(e.getId()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(anyAssignmentFor(e.getId(), userId)));
        when(userRepository.findById(userId)).thenReturn(Optional.of(new UserEntity()));

        var response = service.claim(claimCode.toLowerCase(), userId, "10.0.0.3");

        assertEquals(DeviceStatus.DEVICE_ASSIGNED.code(), e.getStatus());
        assertEquals(claimCode, e.getClaimCode());
        assertNotNull(e.getClaimedAt());
        assertEquals(e.getId(), response.id());
        assertEquals(userId, response.assignedUserId());
        assertEquals(claimCode, response.claimCode());
        var captor = ArgumentCaptor.forClass(
                com.somnguard.device_management.adapter.out.persistence.entity.DeviceAssignmentEntity.class);
        verify(assignmentRepository).save(captor.capture());
        assertEquals(userId, captor.getValue().getUserId());
    }

    @Test
    void assignRejectsWhenUserAlreadyHasDevice() {
        UUID userId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        DeviceEntity e = new DeviceEntity();
        e.setId(deviceId);
        e.setSerialNumber("SN-004");
        e.setStatus(DeviceStatus.DEVICE_REGISTERED.code());
        e.setStatusCategory(DeviceStatus.DEVICE_REGISTERED.category());
        e.setClaimCode(apiKeyService.generateClaimCode());
        when(deviceRepository.findByIdAndDeletedAtIsNull(deviceId)).thenReturn(Optional.of(e));
        when(assignmentRepository.findByDeviceIdAndUnassignedAtIsNullAndDeletedAtIsNull(deviceId))
                .thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(new UserEntity()));
        when(assignmentRepository.findByUserIdAndUnassignedAtIsNullAndDeletedAtIsNull(userId))
                .thenReturn(java.util.List.of(anyAssignmentFor(UUID.randomUUID(), userId)));

        assertThrows(DeviceConflictException.class,
                () -> service.assign(deviceId, userId, adminId, true));
    }

    @Test
    void claimRejectsWhenUserAlreadyHasDevice() {
        UUID userId = UUID.randomUUID();
        String claimCode = apiKeyService.generateClaimCode();
        DeviceEntity e = new DeviceEntity();
        e.setId(UUID.randomUUID());
        e.setStatus(DeviceStatus.DEVICE_REGISTERED.code());
        e.setStatusCategory(DeviceStatus.DEVICE_REGISTERED.category());
        e.setClaimCode(claimCode);
        when(deviceRepository.findByClaimCodeAndDeletedAtIsNull(claimCode))
                .thenReturn(Optional.of(e));
        when(assignmentRepository.findByDeviceIdAndUnassignedAtIsNullAndDeletedAtIsNull(e.getId()))
                .thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(new UserEntity()));
        when(assignmentRepository.findByUserIdAndUnassignedAtIsNullAndDeletedAtIsNull(userId))
                .thenReturn(java.util.List.of(anyAssignmentFor(UUID.randomUUID(), userId)));

        assertThrows(DeviceConflictException.class,
                () -> service.claim(claimCode, userId, null));
    }

    @Test
    void claimWhileAssignedThrowsConflict() {
        String claimCode = apiKeyService.generateClaimCode();
        DeviceEntity e = new DeviceEntity();
        e.setId(UUID.randomUUID());
        e.setStatus(DeviceStatus.DEVICE_ACTIVE.code());
        e.setClaimCode(claimCode);
        when(deviceRepository.findByClaimCodeAndDeletedAtIsNull(claimCode))
                .thenReturn(Optional.of(e));

        assertThrows(DeviceConflictException.class,
                () -> service.claim(claimCode, UUID.randomUUID(), null));
    }

    @Test
    void unassignReleasesClaimCodeForReuse() {
        UUID userId = UUID.randomUUID();
        String claimCode = apiKeyService.generateClaimCode();
        DeviceEntity e = new DeviceEntity();
        e.setId(UUID.randomUUID());
        e.setStatus(DeviceStatus.DEVICE_ASSIGNED.code());
        e.setStatusCategory(DeviceStatus.DEVICE_ASSIGNED.category());
        e.setClaimCode(claimCode);
        e.setClaimedAt(OffsetDateTime.now());
        var active = anyAssignmentFor(e.getId(), userId);
        when(deviceRepository.findByIdAndDeletedAtIsNull(e.getId())).thenReturn(Optional.of(e));
        when(assignmentRepository.findByDeviceIdAndUnassignedAtIsNullAndDeletedAtIsNull(e.getId()))
                .thenReturn(Optional.of(active));

        var response = service.unassign(e.getId(), userId, false);

        assertEquals(DeviceStatus.DEVICE_REGISTERED.code(), e.getStatus());
        assertNull(e.getClaimedAt());
        assertEquals(claimCode, e.getClaimCode());
        assertEquals(claimCode, response.claimCode());
    }

    @Test
    void claimReuseThrowsNotFound() {
        when(deviceRepository.findByClaimCodeAndDeletedAtIsNull(any()))
                .thenReturn(Optional.empty());

        assertThrows(DeviceNotFoundException.class,
                () -> service.claim("XXXX-XXXX-XXXX", UUID.randomUUID(), null));
    }

    @Test
    void manualCreateGeneratesClaimCode() {
        when(deviceRepository.findBySerialNumberAndDeletedAtIsNull("SN-M"))
                .thenReturn(Optional.empty());
        when(deviceRepository.findByClaimCodeAndDeletedAtIsNull(any()))
                .thenReturn(Optional.empty());

        var created = service.create("SN-M", "2.0.0", adminId);

        assertNotNull(created.plainKey());
        assertNotNull(created.plainClaimCode());
        assertEquals(created.plainClaimCode(), created.device().getClaimCode());
    }

    private static com.somnguard.device_management.adapter.out.persistence.entity.DeviceAssignmentEntity anyAssignmentFor(
            UUID deviceId, UUID userId) {
        var a = new com.somnguard.device_management.adapter.out.persistence.entity.DeviceAssignmentEntity();
        a.setId(UUID.randomUUID());
        a.setDeviceId(deviceId);
        a.setUserId(userId);
        a.setAssignedAt(OffsetDateTime.now());
        return a;
    }
}
