package com.somnguard.device_management.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.somnguard.device_management.adapter.out.persistence.entity.DeviceConfigEntity;
import com.somnguard.device_management.adapter.out.persistence.entity.DeviceEntity;
import com.somnguard.device_management.adapter.out.persistence.repository.DeviceConfigHistoryRepository;
import com.somnguard.device_management.adapter.out.persistence.repository.DeviceConfigRepository;
import com.somnguard.device_management.adapter.out.persistence.repository.DeviceRepository;
import com.somnguard.device_management.application.service.DeviceApiKeyService;
import com.somnguard.device_management.domain.exception.DeviceForbiddenException;
import com.somnguard.device_management.domain.exception.InvalidDeviceConfigException;
import com.somnguard.device_management.domain.exception.InvalidDeviceCredentialsException;
import com.somnguard.device_management.domain.model.DeviceStatus;
import com.somnguard.parameterization.adapter.out.persistence.entity.EventTypeEntity;
import com.somnguard.parameterization.adapter.out.persistence.entity.GlobalConfigEntity;
import com.somnguard.parameterization.adapter.out.persistence.entity.SoundPatternEntity;
import com.somnguard.parameterization.adapter.out.persistence.repository.EventTypeRepository;
import com.somnguard.parameterization.adapter.out.persistence.repository.GlobalConfigHistoryRepository;
import com.somnguard.parameterization.adapter.out.persistence.repository.GlobalConfigRepository;
import com.somnguard.parameterization.adapter.out.persistence.repository.SoundPatternRepository;
import com.somnguard.parameterization.application.usecase.GlobalConfigService;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeviceConfigServiceTest {

    @Mock
    DeviceRepository deviceRepository;
    @Mock
    DeviceConfigRepository configRepository;
    @Mock
    DeviceConfigHistoryRepository historyRepository;
    @Mock
    EventTypeRepository eventTypeRepository;
    @Mock
    SoundPatternRepository soundPatternRepository;
    @Mock
    GlobalConfigRepository globalConfigRepository;
    @Mock
    GlobalConfigHistoryRepository globalConfigHistoryRepository;

    final DeviceApiKeyService apiKeyService = new DeviceApiKeyService();
    DeviceConfigService service;

    final UUID adminId = UUID.randomUUID();
    final UUID deviceId = UUID.randomUUID();
    final String plainKey = "test-key-plano";
    DeviceEntity device;
    SoundPatternEntity as01;
    SoundPatternEntity as02;
    EventTypeEntity ev01;

    @BeforeEach
    void setup() {
        var globalConfigService = new GlobalConfigService(globalConfigRepository,
                globalConfigHistoryRepository, eventTypeRepository, soundPatternRepository);
        service = new DeviceConfigService(deviceRepository, configRepository, historyRepository,
                eventTypeRepository, soundPatternRepository, apiKeyService, globalConfigService);
        device = new DeviceEntity();
        device.setId(deviceId);
        device.setSerialNumber("SN-001");
        device.setApiKeyHash(apiKeyService.hash(plainKey));
        device.setIsActive(true);
        device.setStatus(DeviceStatus.DEVICE_ACTIVE.code());
        device.setStatusCategory(DeviceStatus.DEVICE_ACTIVE.category());
        as01 = sound("AS-01");
        as02 = sound("AS-02");
        ev01 = event("EV-SOM-01", Map.of("blink_rate_max", 25));
        ev01.setDefaultSoundPatternId(as01.getId());
    }

    private GlobalConfigEntity globalVersion(int version) {
        GlobalConfigEntity g = new GlobalConfigEntity();
        g.setVersion(version);
        g.setUpdatedAt(OffsetDateTime.now());
        return g;
    }

    private void stubGlobal(int version) {
        when(globalConfigRepository.findById(GlobalConfigEntity.SINGLETON_ID))
                .thenReturn(Optional.of(globalVersion(version)));
    }

    @Test
    void patchCreatesRowAndHistoryWithCanonicalOverrides() {
        when(deviceRepository.findByIdAndDeletedAtIsNull(deviceId)).thenReturn(Optional.of(device));
        when(eventTypeRepository.findByDeletedAtIsNull()).thenReturn(List.of(ev01));
        when(soundPatternRepository.findByIsActiveTrue()).thenReturn(List.of(as01, as02));
        when(configRepository.findByDeviceIdAndDeletedAtIsNull(deviceId)).thenReturn(Optional.empty());

        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("sound_patterns", Map.of("EV-SOM-01", "AS-02"));
        raw.put("volume_pct", 60);
        var result = service.patchConfig(deviceId, raw, "ajuste turno noche", adminId, null);

        assertEquals(deviceId, result.deviceId());
        var cfgCaptor = ArgumentCaptor.forClass(DeviceConfigEntity.class);
        verify(configRepository).save(cfgCaptor.capture());
        assertEquals(Map.of("EV-SOM-01", "AS-02"), cfgCaptor.getValue().getConfiguration().get("event_sound_map"));
        assertEquals(60, cfgCaptor.getValue().getConfiguration().get("volume_pct"));
        var histCaptor = ArgumentCaptor.forClass(
                com.somnguard.device_management.adapter.out.persistence.entity.DeviceConfigHistoryEntity.class);
        verify(historyRepository).save(histCaptor.capture());
        assertEquals(adminId, histCaptor.getValue().getChangedBy());
        assertEquals("ajuste turno noche", histCaptor.getValue().getChangeReason());
    }

    @Test
    void patchUnknownEventIs422() {
        when(deviceRepository.findByIdAndDeletedAtIsNull(deviceId)).thenReturn(Optional.of(device));
        when(eventTypeRepository.findByDeletedAtIsNull()).thenReturn(List.of(ev01));
        when(soundPatternRepository.findByIsActiveTrue()).thenReturn(List.of(as01, as02));

        Map<String, Object> raw = Map.of("thresholds", Map.of("EV-XXX-99", Map.of("x", 1)));

        assertThrows(InvalidDeviceConfigException.class,
                () -> service.patchConfig(deviceId, raw, null, adminId, null));
        verify(configRepository, never()).save(any());
        verify(historyRepository, never()).save(any());
    }

    @Test
    void patchBadRangeIs400() {
        when(deviceRepository.findByIdAndDeletedAtIsNull(deviceId)).thenReturn(Optional.of(device));

        assertThrows(IllegalArgumentException.class,
                () -> service.patchConfig(deviceId, Map.of("volume_pct", 200), null, adminId, null));
        verify(configRepository, never()).save(any());
    }

    @Test
    void patchRequiresAdminChangedBy() {
        when(deviceRepository.findByIdAndDeletedAtIsNull(deviceId)).thenReturn(Optional.of(device));

        assertThrows(DeviceForbiddenException.class, () -> service.patchConfig(deviceId,
                Map.of("volume_pct", 10), null, DeviceService.SYSTEM_ID, null));
    }

    @Test
    void requestRefreshMarksPending() {
        when(deviceRepository.findByIdAndDeletedAtIsNull(deviceId)).thenReturn(Optional.of(device));

        var result = service.requestRefresh(deviceId, adminId, true, List.of("device.config"));

        assertEquals(true, result.get("pending"));
        assertEquals(true, device.getPendingConfigUpdate());
        verify(deviceRepository).save(device);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getReturnsGlobalVersionAndPersistsApplied() {
        when(deviceRepository.findByIdAndDeletedAtIsNull(deviceId)).thenReturn(Optional.of(device));
        when(eventTypeRepository.findByDeletedAtIsNull()).thenReturn(List.of(ev01));
        when(soundPatternRepository.findByIsActiveTrue()).thenReturn(List.of(as01, as02));
        stubGlobal(7);

        var response = service.getEffectiveConfig(deviceId, deviceId, plainKey, false, List.of(), "10.0.0.9");

        assertEquals(7, response.getVersion());
        Map<String, Object> thresholds = (Map<String, Object>) response.getConfigurationEntries().get("thresholds");
        Map<String, Object> ev = (Map<String, Object>) thresholds.get("EV-SOM-01");
        assertEquals(25, ev.get("blink_rate_max"));
        assertEquals(0.8, response.getConfigurationEntries().get("volume_scale"));
        assertEquals(7, response.getSources().get("global_version"));
        assertEquals(7, device.getAppliedConfigVersion());
        assertNotNull(device.getLastConfigPullAt());
        assertEquals(false, device.getPendingConfigUpdate());
        assertEquals("10.0.0.9", device.getLastSeenIp());
        verify(deviceRepository).save(device);
    }

    @Test
    void getIgnoresLegacyOverridesRow() {
        // ADR-011: aunque exista una fila legacy en device_config, el GET es solo global,
        // PERO ahora SÍ persiste el snapshot global en device_config/history.
        DeviceConfigEntity cfg = new DeviceConfigEntity();
        cfg.setId(UUID.randomUUID());
        cfg.setDeviceId(deviceId);
        cfg.setVersion(2);
        Map<String, Object> overrides = new LinkedHashMap<>();
        overrides.put("thresholds", Map.of("EV-SOM-01", Map.of("blink_rate_max", 22)));
        cfg.setConfiguration(overrides);
        when(deviceRepository.findByIdAndDeletedAtIsNull(deviceId)).thenReturn(Optional.of(device));
        when(eventTypeRepository.findByDeletedAtIsNull()).thenReturn(List.of(ev01));
        when(soundPatternRepository.findByIsActiveTrue()).thenReturn(List.of(as01, as02));
        stubGlobal(7);

        var response = service.getEffectiveConfig(deviceId, deviceId, plainKey, false, List.of(), null);

        Map<String, Object> thresholds = (Map<String, Object>) response.getConfigurationEntries().get("thresholds");
        Map<String, Object> ev = (Map<String, Object>) thresholds.get("EV-SOM-01");
        assertEquals(25, ev.get("blink_rate_max"));
        assertEquals(7, response.getVersion());
        // Ahora SÍ persiste en device_config y history (snapshot global)
        verify(configRepository).save(any());
        verify(historyRepository).save(any());
    }

    @Test
    void getWithoutRowReturnsDefaults() {
        when(deviceRepository.findByIdAndDeletedAtIsNull(deviceId)).thenReturn(Optional.of(device));
        when(eventTypeRepository.findByDeletedAtIsNull()).thenReturn(List.of(ev01));
        when(soundPatternRepository.findByIsActiveTrue()).thenReturn(List.of(as01, as02));
        stubGlobal(3);

        var response = service.getEffectiveConfig(deviceId, deviceId, plainKey, false, List.of(), null);

        assertEquals(3, response.getVersion());
        assertTrue(((Map<String, Object>) response.getConfigurationEntries().get("thresholds"))
                .containsKey("EV-SOM-01"));
        assertEquals(3, device.getAppliedConfigVersion());
        // Ahora SÍ persiste en history
        verify(historyRepository).save(any());
        verify(configRepository).save(any());
    }

    @Test
    void getWithBadApiKeyIs401() {
        when(deviceRepository.findByIdAndDeletedAtIsNull(deviceId)).thenReturn(Optional.of(device));

        assertThrows(InvalidDeviceCredentialsException.class, () -> service.getEffectiveConfig(
                deviceId, deviceId, "clave-mala", false, List.of(), null));
    }

    @Test
    void getWithJwtRequiresReadFeature() {
        when(deviceRepository.findByIdAndDeletedAtIsNull(deviceId)).thenReturn(Optional.of(device));

        assertThrows(DeviceForbiddenException.class, () -> service.getEffectiveConfig(
                deviceId, null, null, true, List.of("otro.permiso"), null));
    }

    @Test
    void getWithJwtIsReadOnly() {
        when(deviceRepository.findByIdAndDeletedAtIsNull(deviceId)).thenReturn(Optional.of(device));
        when(eventTypeRepository.findByDeletedAtIsNull()).thenReturn(List.of(ev01));
        when(soundPatternRepository.findByIsActiveTrue()).thenReturn(List.of(as01, as02));
        stubGlobal(7);

        var response = service.getEffectiveConfig(deviceId, null, null, true,
                List.of("device.read"), null);

        assertEquals(7, response.getVersion());
        assertEquals(7, response.getSources().get("global_version"));
        assertEquals(0, device.getAppliedConfigVersion());
        assertEquals(null, device.getLastConfigPullAt());
        verify(deviceRepository, never()).save(any());
        verify(configRepository, never()).save(any());
        verify(historyRepository, never()).save(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void catalogChangePropagatesWithNewGlobalVersion() {
        when(deviceRepository.findByIdAndDeletedAtIsNull(deviceId)).thenReturn(Optional.of(device));
        when(soundPatternRepository.findByIsActiveTrue()).thenReturn(List.of(as01, as02));
        when(eventTypeRepository.findByDeletedAtIsNull())
                .thenReturn(List.of(ev01))
                .thenReturn(List.of(event("EV-SOM-01", Map.of("blink_rate_max", 99))));
        when(globalConfigRepository.findById(GlobalConfigEntity.SINGLETON_ID))
                .thenReturn(Optional.of(globalVersion(7)))
                .thenReturn(Optional.of(globalVersion(8)));

        var first = service.getEffectiveConfig(deviceId, deviceId, plainKey, false, List.of(), null);
        var second = service.getEffectiveConfig(deviceId, deviceId, plainKey, false, List.of(), null);

        assertEquals(7, first.getVersion());
        assertEquals(8, second.getVersion());
        assertEquals(8, device.getAppliedConfigVersion());
        Map<String, Object> t1 = (Map<String, Object>) ((Map<String, Object>) first.getConfigurationEntries()
                .get("thresholds")).get("EV-SOM-01");
        Map<String, Object> t2 = (Map<String, Object>) ((Map<String, Object>) second.getConfigurationEntries()
                .get("thresholds")).get("EV-SOM-01");
        assertEquals(25, t1.get("blink_rate_max"));
        assertEquals(99, t2.get("blink_rate_max"));
        // Ahora SÍ persiste en history/config en cada pull
        verify(historyRepository, times(2)).save(any());
        verify(configRepository, times(2)).save(any());
    }

    private EventTypeEntity event(String code, Map<String, Object> thresholds) {
        EventTypeEntity e = new EventTypeEntity();
        e.setId(UUID.randomUUID());
        e.setCode(code);
        e.setName(code);
        e.setEventCategoryId(UUID.randomUUID());
        e.setDefaultSeverityId(UUID.randomUUID());
        e.setDefaultSoundPatternId(as01.getId());
        e.setThresholdConfig(new LinkedHashMap<>(thresholds));
        e.setIsActive(true);
        e.setStatus("PUBLISHED");
        e.setStatusCategory("ACTIVE");
        e.setUpdatedAt(OffsetDateTime.now());
        return e;
    }

    private SoundPatternEntity sound(String code) {
        SoundPatternEntity s = new SoundPatternEntity();
        s.setId(UUID.randomUUID());
        s.setCode(code);
        s.setDescription(code);
        s.setFrequencyHz(800);
        s.setDurationMs(500);
        s.setRepetitions((short) 1);
        s.setPatternType("beep");
        s.setIsActive(true);
        s.setUpdatedAt(OffsetDateTime.now());
        return s;
    }
}
