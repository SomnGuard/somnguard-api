package com.somnguard.device_management.application.usecase;

import com.somnguard.device_management.adapter.in.web.dto.DevicePageResponse;
import com.somnguard.device_management.adapter.in.web.dto.DeviceResponse;
import com.somnguard.device_management.adapter.in.web.dto.HeartbeatRequest;
import com.somnguard.device_management.adapter.out.persistence.entity.DeviceAssignmentEntity;
import com.somnguard.device_management.adapter.out.persistence.entity.DeviceConfigEntity;
import com.somnguard.device_management.adapter.out.persistence.entity.DeviceEntity;
import com.somnguard.device_management.adapter.out.persistence.entity.DeviceStatusAuditEntity;
import com.somnguard.device_management.adapter.out.persistence.entity.ProvisioningAuditEntity;
import com.somnguard.device_management.adapter.out.persistence.entity.ProvisioningTokenEntity;
import com.somnguard.device_management.adapter.out.persistence.repository.DeviceAssignmentRepository;
import com.somnguard.device_management.adapter.out.persistence.repository.DeviceConfigRepository;
import com.somnguard.device_management.adapter.out.persistence.repository.DeviceRepository;
import com.somnguard.device_management.adapter.out.persistence.repository.DeviceStatusAuditRepository;
import com.somnguard.device_management.adapter.out.persistence.repository.ProvisioningAuditRepository;
import com.somnguard.device_management.adapter.out.persistence.repository.ProvisioningTokenRepository;
import com.somnguard.device_management.application.service.DeviceApiKeyService;
import com.somnguard.device_management.domain.exception.DeviceConflictException;
import com.somnguard.device_management.domain.exception.DeviceForbiddenException;
import com.somnguard.device_management.domain.exception.DeviceNotFoundException;
import com.somnguard.device_management.domain.exception.InvalidDeviceCredentialsException;
import com.somnguard.device_management.domain.exception.InvalidStatusTransitionException;
import com.somnguard.device_management.domain.model.DeviceStatus;
import com.somnguard.device_management.domain.service.DeviceStatusPolicy;
import com.somnguard.security.adapter.out.persistence.repository.UserRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso HU-API-006 (FEA-DEV-LIFECYCLE).
 */
@Service
public class DeviceService {

    static final UUID SYSTEM_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");
    private static final long OFFLINE_TIMEOUT_MINUTES = 5;

    private final DeviceRepository deviceRepository;
    private final DeviceAssignmentRepository assignmentRepository;
    private final DeviceStatusAuditRepository auditRepository;
    private final ProvisioningTokenRepository tokenRepository;
    private final ProvisioningAuditRepository provisioningAuditRepository;
    private final DeviceConfigRepository deviceConfigRepository;
    private final DeviceApiKeyService apiKeyService;
    private final UserRepository userRepository;

    public DeviceService(DeviceRepository deviceRepository,
            DeviceAssignmentRepository assignmentRepository,
            DeviceStatusAuditRepository auditRepository,
            ProvisioningTokenRepository tokenRepository,
            ProvisioningAuditRepository provisioningAuditRepository,
            DeviceConfigRepository deviceConfigRepository,
            DeviceApiKeyService apiKeyService,
            UserRepository userRepository) {
        this.deviceRepository = deviceRepository;
        this.assignmentRepository = assignmentRepository;
        this.auditRepository = auditRepository;
        this.tokenRepository = tokenRepository;
        this.provisioningAuditRepository = provisioningAuditRepository;
        this.deviceConfigRepository = deviceConfigRepository;
        this.apiKeyService = apiKeyService;
        this.userRepository = userRepository;
    }

    public record CreatedDevice(DeviceEntity device, String plainKey, String plainClaimCode) {}
    public record CreatedToken(ProvisioningTokenEntity token, String plainToken) {}
    public record SelfRegistered(DeviceEntity device, String plainKey, String plainClaimCode, boolean created) {}

    // AC-001: alta (admin)
    @Transactional
    public CreatedDevice create(String serialNumber, String firmwareVersion, UUID createdBy) {
        String serial = serialNumber == null ? null : serialNumber.trim();
        String firmware = firmwareVersion == null ? null : firmwareVersion.trim();
        if (serial == null || serial.isEmpty()) throw new IllegalArgumentException("serial_number es requerido");
        if (firmware == null || firmware.isEmpty()) throw new IllegalArgumentException("firmware_version es requerido");
        if (deviceRepository.findBySerialNumberAndDeletedAtIsNull(serial).isPresent()) {
            throw new DeviceConflictException("Ya existe un dispositivo con ese serial_number");
        }
        String plainKey = apiKeyService.generatePlainKey();
        String plainClaim = newClaimCode();
        OffsetDateTime now = OffsetDateTime.now();
        DeviceEntity e = new DeviceEntity();
        e.setId(UUID.randomUUID());
        e.setSerialNumber(serial);
        e.setApiKeyHash(apiKeyService.hash(plainKey));
        e.setClaimCode(plainClaim);
        e.setFirmwareVersion(firmware);
        e.setIsActive(true);
        e.setStatus(DeviceStatus.DEVICE_REGISTERED.code());
        e.setStatusCategory(DeviceStatus.DEVICE_REGISTERED.category());
        e.setCreatedAt(now);
        e.setCreatedBy(createdBy);
        e.setUpdatedAt(now);
        e.setUpdatedBy(createdBy);
        e.setVersion(1);
        e.setPendingConfigUpdate(false);
        deviceRepository.save(e);
        ensureDeviceConfig(e.getId(), createdBy, now);
        audit(e.getId(), null, null, e.getStatus(), e.getStatusCategory(), createdBy, "{\"reason\":\"register\"}");
        return new CreatedDevice(e, plainKey, plainClaim);
    }

    // AC-008: provisioning token (admin + device.provision). Un uso, expira 7d, solo hash en BD.
    @Transactional
    public CreatedToken createProvisioningToken(String serialNumber, UUID createdBy, String ip) {
        String serial = serialNumber == null || serialNumber.trim().isEmpty()
                ? null : serialNumber.trim();
        String plainToken = apiKeyService.generatePlainKey();
        OffsetDateTime now = OffsetDateTime.now();
        ProvisioningTokenEntity t = new ProvisioningTokenEntity();
        t.setId(UUID.randomUUID());
        t.setTokenHash(apiKeyService.hash(plainToken));
        t.setSerialNumber(serial);
        t.setMaxUses((short) 1);
        t.setUsesCount((short) 0);
        t.setExpiresAt(now.plusDays(7));
        t.setCreatedAt(now);
        t.setCreatedBy(createdBy);
        tokenRepository.save(t);
        provisioningAudit(t.getId(), "CREATED", null, createdBy, ip);
        return new CreatedToken(t, plainToken);
    }

    // AC-009: self-register (X-Provision-Token + Idempotency-Key). Idempotente por serial:
    // reintento mismo token+serial -> 200 sin reexponer api_key/claim_code.
    @Transactional
    public SelfRegistered selfRegister(String serialNumber, String firmwareVersion,
            String provisionToken, String ip) {
        String serial = serialNumber == null ? null : serialNumber.trim();
        String firmware = firmwareVersion == null ? null : firmwareVersion.trim();
        if (serial == null || serial.isEmpty()) throw new IllegalArgumentException("serial_number es requerido");
        if (firmware == null || firmware.isEmpty()) throw new IllegalArgumentException("firmware_version es requerido");
        if (provisionToken == null || provisionToken.trim().isEmpty()) {
            throw new InvalidDeviceCredentialsException("Token de aprovisionamiento inválido o expirado");
        }
        ProvisioningTokenEntity token = tokenRepository
                .findByTokenHash(apiKeyService.hash(provisionToken.trim()))
                .orElseThrow(() -> new InvalidDeviceCredentialsException(
                        "Token de aprovisionamiento inválido o expirado"));
        if (token.getSerialNumber() != null && !token.getSerialNumber().equalsIgnoreCase(serial)) {
            throw new InvalidDeviceCredentialsException("Token de aprovisionamiento inválido o expirado");
        }
        // Reintento idempotente: mismo token ya generó este serial -> devolver sin reexponer secretos.
        var existing = deviceRepository.findBySerialNumberAndDeletedAtIsNull(serial);
        if (existing.isPresent() && token.getId().equals(existing.get().getProvisioningTokenId())) {
            return new SelfRegistered(existing.get(), null, null, false);
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (token.getRevokedAt() != null
                || !now.isBefore(token.getExpiresAt())
                || token.getUsesCount() >= token.getMaxUses()) {
            throw new InvalidDeviceCredentialsException("Token de aprovisionamiento inválido o expirado");
        }
        if (existing.isPresent()) {
            throw new DeviceConflictException("Ya existe un dispositivo con ese serial_number");
        }
        String plainKey = apiKeyService.generatePlainKey();
        String plainClaim = newClaimCode();
        DeviceEntity e = new DeviceEntity();
        e.setId(UUID.randomUUID());
        e.setSerialNumber(serial);
        e.setApiKeyHash(apiKeyService.hash(plainKey));
        e.setClaimCode(plainClaim);
        e.setFirmwareVersion(firmware);
        e.setIsActive(true);
        e.setStatus(DeviceStatus.DEVICE_REGISTERED.code());
        e.setStatusCategory(DeviceStatus.DEVICE_REGISTERED.category());
        e.setProvisioningTokenId(token.getId());
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        e.setVersion(1);
        e.setPendingConfigUpdate(false);
        deviceRepository.save(e);
        ensureDeviceConfig(e.getId(), null, now);
        audit(e.getId(), null, null, e.getStatus(), e.getStatusCategory(), null,
                "{\"reason\":\"self-register\"}");
        token.setUsesCount((short) (token.getUsesCount() + 1));
        token.setDeviceId(e.getId());
        tokenRepository.save(token);
        provisioningAudit(token.getId(), "USED", e.getId(), null, ip);
        return new SelfRegistered(e, plainKey, plainClaim, true);
    }

    // AC-010: claim (user + device.claim). El código es permanente por device y solo sirve en
    // REGISTERED; si está asignado responde 409 hasta que se libere (unassign -> REGISTERED, claimed_at NULL).
    @Transactional
    public DeviceResponse claim(String claimCode, UUID caller, String ip) {
        if (claimCode == null || claimCode.trim().isEmpty()) {
            throw new IllegalArgumentException("claim_code es requerido");
        }
        DeviceEntity e = deviceRepository
                .findByClaimCodeAndDeletedAtIsNull(normalizeClaimCode(claimCode))
                .orElseThrow(() -> new DeviceNotFoundException("Código de reclamo inválido"));
        DeviceStatus current = DeviceStatus.fromCode(e.getStatus());
        if (current == null) current = DeviceStatus.DEVICE_REGISTERED;
        if (current != DeviceStatus.DEVICE_REGISTERED) {
            throw new DeviceConflictException("Dispositivo ya asignado (estado " + current.code()
                    + "); libérelo con unassign antes de reclamarlo");
        }
        if (assignmentRepository.findByDeviceIdAndUnassignedAtIsNullAndDeletedAtIsNull(e.getId()).isPresent()) {
            throw new DeviceConflictException("Dispositivo ya tiene una asignación activa");
        }
        if (userRepository.findById(caller).isEmpty()) {
            throw new DeviceNotFoundException("Usuario no encontrado");
        }
        // RN-DEV-01: 1 usuario ↔ 1 device vigente (lado usuario)
        if (!assignmentRepository.findByUserIdAndUnassignedAtIsNullAndDeletedAtIsNull(caller).isEmpty()) {
            throw new DeviceConflictException("El usuario ya tiene un dispositivo asignado");
        }
        OffsetDateTime now = OffsetDateTime.now();
        DeviceAssignmentEntity a = new DeviceAssignmentEntity();
        a.setId(UUID.randomUUID());
        a.setDeviceId(e.getId());
        a.setUserId(caller);
        a.setAssignedAt(now);
        a.setAssignedBy(caller);
        a.setCreatedAt(now);
        a.setCreatedBy(caller);
        a.setUpdatedAt(now);
        a.setUpdatedBy(caller);
        a.setIsActive(true);
        a.setVersion(1);
        assignmentRepository.save(a);
        e.setClaimedAt(now);
        applyStatus(e, DeviceStatus.DEVICE_ASSIGNED, caller, "{\"reason\":\"claim\"}");
        e.setUpdatedAt(now);
        e.setUpdatedBy(caller);
        deviceRepository.save(e);
        if (e.getProvisioningTokenId() != null) {
            provisioningAudit(e.getProvisioningTokenId(), "CLAIMED", e.getId(), caller, ip);
        }
        return toResponse(e);
    }

    // AC-005: listado con filtros estado, fecha asignación y paginación
    @Transactional(readOnly = true)
    public DevicePageResponse list(String status, OffsetDateTime assignedFrom, OffsetDateTime assignedTo,
            UUID scopeUserId, boolean adminView, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        Pageable pageable = PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        String normalizedStatus = normalizeStatus(status);

        List<UUID> dateFilteredIds = null;
        if (assignedFrom != null || assignedTo != null) {
            OffsetDateTime from = assignedFrom != null ? assignedFrom : OffsetDateTime.parse("1970-01-01T00:00:00Z");
            OffsetDateTime to = assignedTo != null ? assignedTo : OffsetDateTime.now().plusYears(1);
            List<DeviceAssignmentEntity> matches;
            if (!adminView && scopeUserId != null) {
                matches = assignmentRepository.findByUserIdAndAssignedAtBetween(scopeUserId, from, to);
            } else {
                matches = assignmentRepository.findByAssignedAtBetween(from, to);
            }
            dateFilteredIds = matches.stream().map(DeviceAssignmentEntity::getDeviceId).distinct().toList();
            if (dateFilteredIds.isEmpty()) {
                return new DevicePageResponse(List.of(),
                        new DevicePageResponse.Pagination(safePage, safeSize, 0, 0));
            }
        }

        // Vista no-admin: solo dispositivos propios (+ filtro fecha ya aplicado)
        if (!adminView && scopeUserId != null && dateFilteredIds == null) {
            List<DeviceAssignmentEntity> own = assignmentRepository
                    .findByUserIdAndUnassignedAtIsNullAndDeletedAtIsNull(scopeUserId);
            List<UUID> ownIds = own.stream().map(DeviceAssignmentEntity::getDeviceId).distinct().toList();
            if (ownIds.isEmpty()) {
                return new DevicePageResponse(List.of(),
                        new DevicePageResponse.Pagination(safePage, safeSize, 0, 0));
            }
            dateFilteredIds = ownIds;
        }

        Page<DeviceEntity> result;
        if (dateFilteredIds != null && normalizedStatus != null) {
            result = deviceRepository.findByIdInAndStatusAndDeletedAtIsNull(dateFilteredIds, normalizedStatus, pageable);
        } else if (dateFilteredIds != null) {
            result = deviceRepository.findByIdInAndDeletedAtIsNull(dateFilteredIds, pageable);
        } else if (normalizedStatus != null) {
            result = deviceRepository.findByStatusAndDeletedAtIsNull(normalizedStatus, pageable);
        } else {
            result = deviceRepository.findByDeletedAtIsNull(pageable);
        }

        List<DeviceResponse> data = result.getContent().stream().map(this::toResponse).toList();
        int totalPages = result.getTotalPages();
        return new DevicePageResponse(data, new DevicePageResponse.Pagination(
                safePage, safeSize, result.getTotalElements(), totalPages));
    }

    @Transactional(readOnly = true)
    public DeviceResponse get(UUID id) {
        return toResponse(requireDevice(id));
    }

    // PUT: firmware y/o transición admin (suspender/retirar/reactivar)
    @Transactional
    public DeviceResponse update(UUID id, String firmwareVersion, String status, UUID caller) {
        DeviceEntity e = requireDevice(id);
        DeviceStatus current = DeviceStatus.fromCode(e.getStatus());
        if (current == null) current = DeviceStatus.DEVICE_REGISTERED;
        boolean changed = false;
        if (firmwareVersion != null && !firmwareVersion.trim().isEmpty()
                && !firmwareVersion.trim().equals(e.getFirmwareVersion())) {
            e.setFirmwareVersion(firmwareVersion.trim());
            changed = true;
        }
        if (status != null && !status.trim().isEmpty()) {
            DeviceStatus target = DeviceStatus.fromCode(status.trim().toUpperCase());
            if (target == null) throw new IllegalArgumentException("status desconocido: " + status);
            if (target != current) {
                if (!DeviceStatusPolicy.isAllowed(current, target)) {
                    throw new InvalidStatusTransitionException(
                            "Transición no permitida: " + current.code() + " -> " + target.code());
                }
                // Unassign vía PUT no permitido: usar /unassign para cerrar la asignación
                if (target == DeviceStatus.DEVICE_REGISTERED) {
                    throw new InvalidStatusTransitionException("Use POST /devices/{id}/unassign para liberar el dispositivo");
                }
                // Suspender/retirar/reactivar cierra o mantiene asignación según caso
                if (target == DeviceStatus.DEVICE_RETIRED) {
                    closeActiveAssignment(id, caller, "retire");
                }
                applyStatus(e, target, caller, "{\"reason\":\"admin-update\"}");
                changed = true;
            }
        }
        if (changed) {
            e.setUpdatedAt(OffsetDateTime.now());
            e.setUpdatedBy(caller);
            deviceRepository.save(e);
        }
        return toResponse(e);
    }

    // AC-002: assign
    @Transactional
    public DeviceResponse assign(UUID deviceId, UUID targetUserId, UUID caller, boolean admin) {
        DeviceEntity e = requireDevice(deviceId);
        DeviceStatus current = DeviceStatus.fromCode(e.getStatus());
        if (current == null) current = DeviceStatus.DEVICE_REGISTERED;
        if (current == DeviceStatus.DEVICE_RETIRED) {
            throw new InvalidStatusTransitionException("Dispositivo retirado, no reactivable");
        }
        if (current == DeviceStatus.DEVICE_SUSPENDED) {
            throw new InvalidStatusTransitionException("Dispositivo suspendido, reactívelo un admin antes de asignar");
        }
        if (current != DeviceStatus.DEVICE_REGISTERED) {
            throw new DeviceConflictException("Dispositivo ya asignado (estado " + current.code() + ")");
        }
        if (assignmentRepository.findByDeviceIdAndUnassignedAtIsNullAndDeletedAtIsNull(deviceId).isPresent()) {
            throw new DeviceConflictException("Dispositivo ya tiene una asignación activa");
        }
        UUID target = targetUserId != null ? targetUserId : caller;
        if (!admin && !target.equals(caller)) {
            throw new DeviceForbiddenException("Solo puede asociar el dispositivo a su propia cuenta");
        }
        if (userRepository.findById(target).isEmpty()) {
            throw new DeviceNotFoundException("Usuario no encontrado");
        }
        // RN-DEV-01: 1 usuario ↔ 1 device vigente (lado usuario)
        if (!assignmentRepository.findByUserIdAndUnassignedAtIsNullAndDeletedAtIsNull(target).isEmpty()) {
            throw new DeviceConflictException("El usuario ya tiene un dispositivo asignado");
        }
        OffsetDateTime now = OffsetDateTime.now();
        DeviceAssignmentEntity a = new DeviceAssignmentEntity();
        a.setId(UUID.randomUUID());
        a.setDeviceId(deviceId);
        a.setUserId(target);
        a.setAssignedAt(now);
        a.setAssignedBy(caller);
        a.setCreatedAt(now);
        a.setCreatedBy(caller);
        a.setUpdatedAt(now);
        a.setUpdatedBy(caller);
        a.setIsActive(true);
        a.setVersion(1);
        assignmentRepository.save(a);
        applyStatus(e, DeviceStatus.DEVICE_ASSIGNED, caller, "{\"reason\":\"assign\",\"user_id\":\"" + target + "\"}");
        e.setUpdatedAt(now);
        e.setUpdatedBy(caller);
        deviceRepository.save(e);
        return toResponse(e);
    }

    // AC-003: unassign
    @Transactional
    public DeviceResponse unassign(UUID deviceId, UUID caller, boolean admin) {
        DeviceEntity e = requireDevice(deviceId);
        DeviceStatus current = DeviceStatus.fromCode(e.getStatus());
        if (current == null) current = DeviceStatus.DEVICE_REGISTERED;
        if (!DeviceStatusPolicy.isUnassignTransition(current)) {
            throw new InvalidStatusTransitionException(
                    "Solo se puede liberar desde Asignado/Activo/Offline (actual " + current.code() + ")");
        }
        DeviceAssignmentEntity active = assignmentRepository
                .findByDeviceIdAndUnassignedAtIsNullAndDeletedAtIsNull(deviceId)
                .orElseThrow(() -> new DeviceConflictException("Dispositivo sin asignación activa"));
        if (!admin && !active.getUserId().equals(caller)) {
            throw new DeviceForbiddenException("Solo el propietario o un admin puede liberar el dispositivo");
        }
        closeAssignment(active, caller, "unassign");
        e.setClaimedAt(null); // libera el claim_code: el mismo código vuelve a servir
        applyStatus(e, DeviceStatus.DEVICE_REGISTERED, caller, "{\"reason\":\"unassign\"}");
        e.setUpdatedAt(OffsetDateTime.now());
        e.setUpdatedBy(caller);
        deviceRepository.save(e);
        return toResponse(e);
    }

    // AC-006: heartbeat - solo salud, expone pending_config para que el device decida si pulla
    @Transactional
    public DeviceResponse heartbeat(UUID pathId, UUID headerDeviceId, String plainKey,
            HeartbeatRequest body, String seenIp) {
        if (headerDeviceId == null || plainKey == null || plainKey.isEmpty()) {
            throw new InvalidDeviceCredentialsException("Headers X-Device-ID y X-API-Key son obligatorios");
        }
        if (!pathId.equals(headerDeviceId)) {
            throw new InvalidDeviceCredentialsException("X-Device-ID no coincide con el dispositivo de la ruta");
        }
        DeviceEntity e = requireDevice(pathId);
        if (Boolean.FALSE.equals(e.getIsActive())) {
            throw new DeviceForbiddenException("Dispositivo inactivo");
        }
        if (!apiKeyService.verify(plainKey, e.getApiKeyHash())) {
            throw new InvalidDeviceCredentialsException("API key inválida");
        }
        DeviceStatus current = DeviceStatus.fromCode(e.getStatus());
        if (current == null) current = DeviceStatus.DEVICE_REGISTERED;
        switch (current) {
            case DEVICE_REGISTERED ->
                throw new DeviceForbiddenException("Dispositivo sin asignar, asócielo a una cuenta antes de enviar heartbeat");
            case DEVICE_SUSPENDED -> throw new DeviceForbiddenException("Dispositivo suspendido por un administrador");
            case DEVICE_RETIRED -> throw new DeviceForbiddenException("Dispositivo retirado, no reactivable");
            default -> { }
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (body != null && body.firmwareVersion() != null && !body.firmwareVersion().trim().isEmpty()) {
            e.setFirmwareVersion(body.firmwareVersion().trim());
        }
        e.setLastHeartbeatAt(now);
        if (seenIp != null && !seenIp.isBlank()) {
            e.setLastSeenIp(seenIp.length() > 45 ? seenIp.substring(0, 45) : seenIp);
        }
        if (current == DeviceStatus.DEVICE_ASSIGNED || current == DeviceStatus.DEVICE_OFFLINE) {
            applyStatus(e, DeviceStatus.DEVICE_ACTIVE, SYSTEM_ID, "{\"reason\":\"heartbeat\"}");
        }
        e.setUpdatedAt(now);
        deviceRepository.save(e);
        return toResponse(e);
    }

    // AC-007: rotación (solo admin, estado no cambia)
    public record RotatedKey(DeviceEntity device, String plainKey) {}
    @Transactional
    public RotatedKey rotateKey(UUID id, UUID caller) {
        DeviceEntity e = requireDevice(id);
        String plainKey = apiKeyService.generatePlainKey();
        e.setApiKeyHash(apiKeyService.hash(plainKey));
        e.setUpdatedAt(OffsetDateTime.now());
        e.setUpdatedBy(caller);
        deviceRepository.save(e);
        return new RotatedKey(e, plainKey);
    }

    // AC-004: barrido Offline (>5min sin heartbeat)
    @Transactional
    public int sweepOffline() {
        OffsetDateTime limit = OffsetDateTime.now().minusMinutes(OFFLINE_TIMEOUT_MINUTES);
        List<DeviceEntity> stale = deviceRepository
                .findByStatusAndLastHeartbeatAtBeforeAndDeletedAtIsNull(
                        DeviceStatus.DEVICE_ACTIVE.code(), limit);
        int count = 0;
        for (DeviceEntity e : stale) {
            applyStatus(e, DeviceStatus.DEVICE_OFFLINE, SYSTEM_ID, "{\"reason\":\"heartbeat-timeout\"}");
            e.setUpdatedAt(OffsetDateTime.now());
            deviceRepository.save(e);
            count++;
        }
        return count;
    }

    DeviceEntity requireDevice(UUID id) {
        return deviceRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new DeviceNotFoundException("Dispositivo no encontrado"));
    }

    public DeviceEntity requireDevicePublic(UUID id) { return requireDevice(id); }

    private void applyStatus(DeviceEntity e, DeviceStatus target, UUID changedBy, String contextJson) {
        DeviceStatus current = DeviceStatus.fromCode(e.getStatus());
        if (current != null && !DeviceStatusPolicy.isAllowed(current, target)) {
            throw new InvalidStatusTransitionException(
                    "Transición no permitida: " + current.code() + " -> " + target.code());
        }
        String fromCode = e.getStatus();
        String fromCat = e.getStatusCategory();
        e.setStatus(target.code());
        e.setStatusCategory(target.category());
        audit(e.getId(), fromCode, fromCat, target.code(), target.category(), changedBy, contextJson);
    }

    private void audit(UUID deviceId, String from, String fromCat, String to, String toCat,
            UUID changedBy, String contextJson) {
        DeviceStatusAuditEntity a = new DeviceStatusAuditEntity();
        a.setDeviceId(deviceId);
        a.setFromStatus(from);
        a.setFromCategory(fromCat);
        a.setToStatus(to);
        a.setToCategory(toCat);
        a.setChangedBy(changedBy);
        a.setChangedAt(OffsetDateTime.now());
        a.setContextJson(contextJson);
        auditRepository.save(a);
    }

    private void provisioningAudit(UUID tokenId, String action, UUID deviceId, UUID actorId, String ip) {
        ProvisioningAuditEntity p = new ProvisioningAuditEntity();
        p.setId(UUID.randomUUID());
        p.setTokenId(tokenId);
        p.setAction(action);
        p.setDeviceId(deviceId);
        p.setActorId(actorId);
        p.setIpAddress(ip == null || ip.isBlank() ? null : (ip.length() > 45 ? ip.substring(0, 45) : ip));
        p.setCreatedAt(OffsetDateTime.now());
        provisioningAuditRepository.save(p);
    }

    private void closeAssignment(DeviceAssignmentEntity a, UUID caller, String reason) {
        OffsetDateTime now = OffsetDateTime.now();
        a.setUnassignedAt(now);
        a.setDeletedAt(now);
        a.setDeletedBy(caller);
        a.setIsActive(false);
        a.setUpdatedAt(now);
        a.setUpdatedBy(caller);
        assignmentRepository.save(a);
    }

    private void closeActiveAssignment(UUID deviceId, UUID caller, String reason) {
        assignmentRepository.findByDeviceIdAndUnassignedAtIsNullAndDeletedAtIsNull(deviceId)
                .ifPresent(a -> closeAssignment(a, caller, reason));
    }

    private String newClaimCode() {
        for (int i = 0; i < 5; i++) {
            String code = apiKeyService.generateClaimCode();
            if (deviceRepository.findByClaimCodeAndDeletedAtIsNull(code).isEmpty()) {
                return code;
            }
        }
        throw new IllegalStateException("No se pudo generar un claim_code único");
    }

    private static String normalizeClaimCode(String claimCode) {
        return claimCode.trim().toUpperCase();
    }

    private String normalizeStatus(String status) {        if (status == null || status.isBlank()) return null;
        DeviceStatus s = DeviceStatus.fromCode(status.trim().toUpperCase());
        if (s == null) throw new IllegalArgumentException("status desconocido: " + status);
        return s.code();
    }

    DeviceResponse toResponse(DeviceEntity e) {
        var active = assignmentRepository
                .findByDeviceIdAndUnassignedAtIsNullAndDeletedAtIsNull(e.getId());
        UUID assignedUser = active.map(DeviceAssignmentEntity::getUserId).orElse(null);
        OffsetDateTime assignedAt = active.map(DeviceAssignmentEntity::getAssignedAt).orElse(null);
        return new DeviceResponse(e.getId(), e.getSerialNumber(), e.getFirmwareVersion(),
                e.getStatus(), e.getStatusCategory(), e.getLastHeartbeatAt(), e.getLastSeenIp(),
                assignedUser, assignedAt, e.getClaimCode(), e.getCreatedAt(), e.getUpdatedAt(),
                e.getAppliedConfigVersion(), e.getPendingConfigUpdate(), e.getLastConfigPullAt());
    }

    public List<DeviceResponse> toResponses(List<DeviceEntity> entities) {
        List<DeviceResponse> out = new ArrayList<>(entities.size());
        for (DeviceEntity e : entities) out.add(toResponse(e));
        return out;
    }

    private void ensureDeviceConfig(UUID deviceId, UUID createdBy, OffsetDateTime now) {
        if (deviceConfigRepository.findByDeviceIdAndDeletedAtIsNull(deviceId).isPresent()) return;
        DeviceConfigEntity cfg = new DeviceConfigEntity();
        cfg.setId(UUID.randomUUID());
        cfg.setDeviceId(deviceId);
        cfg.setConfiguration(new java.util.LinkedHashMap<>());
        cfg.setIsActive(true);
        cfg.setVersion(1);
        cfg.setStatus("DEVICE_CONFIG_PUBLISHED");
        cfg.setStatusCategory("ACTIVE");
        cfg.setPublishedAt(now);
        cfg.setCreatedAt(now);
        cfg.setCreatedBy(createdBy);
        cfg.setUpdatedAt(now);
        cfg.setUpdatedBy(createdBy);
        deviceConfigRepository.save(cfg);
    }
}
