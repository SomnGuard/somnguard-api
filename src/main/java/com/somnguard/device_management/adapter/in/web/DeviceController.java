package com.somnguard.device_management.adapter.in.web;

import com.somnguard.device_management.adapter.in.web.dto.AssignDeviceRequest;
import com.somnguard.device_management.adapter.in.web.dto.ClaimDeviceRequest;
import com.somnguard.device_management.adapter.in.web.dto.CreateDeviceRequest;
import com.somnguard.device_management.adapter.in.web.dto.CreateDeviceResponse;
import com.somnguard.device_management.adapter.in.web.dto.DevicePageResponse;
import com.somnguard.device_management.adapter.in.web.dto.DeviceResponse;
import com.somnguard.device_management.adapter.in.web.dto.HeartbeatRequest;
import com.somnguard.device_management.adapter.in.web.dto.HeartbeatResponse;
import com.somnguard.device_management.adapter.in.web.dto.ProvisioningTokenRequest;
import com.somnguard.device_management.adapter.in.web.dto.ProvisioningTokenResponse;
import com.somnguard.device_management.adapter.in.web.dto.RotateKeyResponse;
import com.somnguard.device_management.adapter.in.web.dto.SelfRegisterRequest;
import com.somnguard.device_management.adapter.in.web.dto.SelfRegisterResponse;
import com.somnguard.device_management.adapter.in.web.dto.UpdateDeviceRequest;
import com.somnguard.device_management.application.usecase.DeviceService;
import com.somnguard.parameterization.application.usecase.GlobalConfigService;
import com.somnguard.platform.security.RequireFeature;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gestión de dispositivos (HU-API-006, FEA-DEV-LIFECYCLE).
 * Prefijo {@code /api/v1} y envolventes según {@code docs/07-api-design}.
 */
@RestController
@RequestMapping("/api/v1/devices")
public class DeviceController {

    private final DeviceService deviceService;
    private final GlobalConfigService globalConfigService;

    public DeviceController(DeviceService deviceService, GlobalConfigService globalConfigService) {
        this.deviceService = deviceService;
        this.globalConfigService = globalConfigService;
    }

    // AC-001: alta (admin). Idempotencia: acepta Idempotency-Key (reintentos del portal).
    @PostMapping
    @RequireFeature("device.write")
    public ResponseEntity<CreateDeviceResponse> create(
            @Valid @RequestBody CreateDeviceRequest req,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        var created = deviceService.create(req.serialNumber(), req.firmwareVersion(), DeviceAuthSupport.currentUserId());
        var d = created.device();
        CreateDeviceResponse body = new CreateDeviceResponse(
                d.getId(), d.getSerialNumber(), d.getFirmwareVersion(), d.getStatus(),
                created.plainKey(), created.plainClaimCode());
        return ResponseEntity.created(URI.create("/api/v1/devices/" + d.getId())).body(body);
    }

    // AC-008: provisioning (admin + device.provision). Token un uso, expira 7d, solo hash en BD;
    // el valor en claro se expone una única vez (201).
    @PostMapping("/provisioning-tokens")
    @RequireFeature("device.provision")
    public ResponseEntity<ProvisioningTokenResponse> createProvisioningToken(
            @Valid @RequestBody(required = false) ProvisioningTokenRequest req,
            HttpServletRequest http) {
        var created = deviceService.createProvisioningToken(
                req == null ? null : req.serialNumber(),
                DeviceAuthSupport.currentUserId(), clientIp(http));
        var t = created.token();
        return ResponseEntity.created(URI.create("/api/v1/devices/provisioning-tokens/" + t.getId()))
                .body(new ProvisioningTokenResponse(t.getId(), created.plainToken(),
                        t.getExpiresAt(), t.getMaxUses()));
    }

    // AC-009: self-register (X-Provision-Token + Idempotency-Key, sin JWT).
    // 201 expone api_key + claim_code una sola vez; reintento -> 200 sin reexponerlos.
    @PostMapping("/self-register")
    public ResponseEntity<SelfRegisterResponse> selfRegister(
            @RequestHeader(value = "X-Provision-Token", required = false) String provisionToken,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody SelfRegisterRequest req,
            HttpServletRequest http) {
        var result = deviceService.selfRegister(
                req.serialNumber(), req.firmwareVersion(), provisionToken, clientIp(http));
        var d = result.device();
        SelfRegisterResponse body = new SelfRegisterResponse(
                d.getId(), d.getStatus(), result.plainKey(), result.plainClaimCode());
        if (result.created()) {
            return ResponseEntity.created(URI.create("/api/v1/devices/" + d.getId())).body(body);
        }
        return ResponseEntity.ok(body);
    }

    // AC-010: claim (usuario + device.claim). Código permanente por device, solo en REGISTERED;
    // unassign lo libera y el mismo código re-sirve.
    @PostMapping("/claim")
    @RequireFeature("device.claim")
    public ResponseEntity<DeviceResponse> claim(
            @Valid @RequestBody ClaimDeviceRequest req,
            HttpServletRequest http) {
        DeviceResponse d = deviceService.claim(
                req.claimCode(), DeviceAuthSupport.currentUserId(), clientIp(http));
        return ResponseEntity.created(URI.create("/api/v1/devices/" + d.id())).body(d);
    }

    // AC-005: filtros estado, fecha asignación + paginación {data, pagination}
    // user own via device.assign (matriz 25 features); admin via device.read/write.
    @GetMapping
    @RequireFeature({"device.read", "device.write", "device.assign"})
    public DevicePageResponse list(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "assigned_from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime assignedFrom,
            @RequestParam(value = "assigned_to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime assignedTo,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "page_size", defaultValue = "20") int pageSize) {
        boolean admin = DeviceAuthSupport.isAdmin();
        UUID scopeUser = admin ? null : DeviceAuthSupport.currentUserId();
        return deviceService.list(status, assignedFrom, assignedTo, scopeUser, admin, page, pageSize);
    }

    @GetMapping("/{id}")
    @RequireFeature({"device.read", "device.write", "device.assign"})
    public DeviceResponse getById(@PathVariable UUID id) {
        DeviceResponse d = deviceService.get(id);
        enforceOwnership(d);
        return d;
    }

    // Actualización (admin): firmware y/o transición (suspender/retirar/reactivar).
    @PutMapping("/{id}")
    @RequireFeature("device.write")
    public DeviceResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateDeviceRequest req) {
        return toScoped(deviceService.update(id,
                req == null ? null : req.firmwareVersion(),
                req == null ? null : req.status(),
                DeviceAuthSupport.currentUserId()));
    }

    // AC-002: assign (usuario autenticado asocia a su cuenta; admin puede asignar a otro).
    @PostMapping("/{id}/assign")
    @RequireFeature({"device.read", "device.write", "device.assign"})
    public DeviceResponse assign(@PathVariable UUID id,
            @RequestBody(required = false) AssignDeviceRequest req,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        UUID caller = DeviceAuthSupport.currentUserId();
        boolean admin = DeviceAuthSupport.isAdmin();
        UUID target = (req != null) ? req.userId() : null;
        return deviceService.assign(id, target, caller, admin);
    }

    // AC-003: unassign (propietario o admin) -> Registrado
    @PostMapping("/{id}/unassign")
    @RequireFeature({"device.read", "device.write", "device.assign"})
    public DeviceResponse unassign(@PathVariable UUID id) {
        return deviceService.unassign(id, DeviceAuthSupport.currentUserId(), DeviceAuthSupport.isAdmin());
    }

    // AC-006: heartbeat (auth X-Device-ID + X-API-Key, sin JWT).
    @PostMapping("/{id}/heartbeat")
    public HeartbeatResponse heartbeat(@PathVariable UUID id,
            @RequestHeader(value = "X-Device-ID", required = false) String deviceIdHeader,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @Valid @RequestBody(required = false) HeartbeatRequest body,
            HttpServletRequest http) {
        UUID headerId = null;
        if (deviceIdHeader != null && !deviceIdHeader.isBlank()) {
            try {
                headerId = UUID.fromString(deviceIdHeader.trim());
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("X-Device-ID debe ser un UUID válido");
            }
        }
        String ip = clientIp(http);
        DeviceResponse d = deviceService.heartbeat(id, headerId, apiKey, body, ip);
        // ADR-011 manual: configPending = solo flag manual (POST /refresh).
        // applied < global NO activa pending (evita auto-pull del device);
        // la app detecta desactualizado vía GET /config/status (outdated) o applied vs available.
        boolean pending = false;
        int available = 1;
        try {
            var entity = deviceService.requireDevicePublic(id);
            var status = globalConfigService.statusFor(
                    entity.getAppliedConfigVersion(), entity.getPendingConfigUpdate());
            pending = status.pending();
            available = status.availableVersion();
        } catch (Exception ignored) { pending = false; }
        return new HeartbeatResponse(d.id(), d.status(), d.lastHeartbeatAt(), pending, available);
    }

    // AC-007: rotación (solo admin JWT, estado no cambia, key nueva una sola vez).
    @PatchMapping("/{id}/rotate-key")
    @RequireFeature("device.write")
    public RotateKeyResponse rotateKey(@PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        if (!DeviceAuthSupport.isJwt()) {
            throw new com.somnguard.device_management.domain.exception.DeviceForbiddenException(
                    "La rotación requiere autenticación de usuario administrador (JWT)");
        }
        var rotated = deviceService.rotateKey(id, DeviceAuthSupport.currentUserId());
        return new RotateKeyResponse(rotated.device().getId(), rotated.plainKey(), OffsetDateTime.now());
    }

    private DeviceResponse toScoped(DeviceResponse d) {
        enforceOwnership(d);
        return d;
    }

    private void enforceOwnership(DeviceResponse d) {
        if (DeviceAuthSupport.isAdmin()) return;
        if (!DeviceAuthSupport.isJwt()) return; // llamada con API key (heartbeat interno)
        UUID me = DeviceAuthSupport.currentUserId();
        if (d.assignedUserId() != null && !d.assignedUserId().equals(me)) {
            throw new com.somnguard.device_management.domain.exception.DeviceForbiddenException(
                    "No tiene acceso a este dispositivo");
        }
    }

    private String clientIp(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return http.getRemoteAddr();
    }
}
