package com.somnguard.parameterization.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.somnguard.parameterization.adapter.out.persistence.entity.EventTypeEntity;
import com.somnguard.parameterization.adapter.out.persistence.entity.GlobalConfigEntity;
import com.somnguard.parameterization.adapter.out.persistence.entity.GlobalConfigHistoryEntity;
import com.somnguard.parameterization.adapter.out.persistence.entity.SoundPatternEntity;
import com.somnguard.parameterization.adapter.out.persistence.repository.EventTypeRepository;
import com.somnguard.parameterization.adapter.out.persistence.repository.GlobalConfigHistoryRepository;
import com.somnguard.parameterization.adapter.out.persistence.repository.GlobalConfigRepository;
import com.somnguard.parameterization.adapter.out.persistence.repository.SoundPatternRepository;
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
class GlobalConfigServiceTest {

    @Mock
    GlobalConfigRepository globalConfigRepository;
    @Mock
    GlobalConfigHistoryRepository historyRepository;
    @Mock
    EventTypeRepository eventTypeRepository;
    @Mock
    SoundPatternRepository soundPatternRepository;

    GlobalConfigService service;
    final UUID actor = UUID.randomUUID();
    SoundPatternEntity as01;
    EventTypeEntity ev01;

    @BeforeEach
    void setup() {
        service = new GlobalConfigService(globalConfigRepository, historyRepository,
                eventTypeRepository, soundPatternRepository);
        as01 = new SoundPatternEntity();
        as01.setId(UUID.randomUUID());
        as01.setCode("AS-01");
        as01.setDescription("AS-01");
        as01.setFrequencyHz(800);
        as01.setDurationMs(500);
        as01.setRepetitions((short) 1);
        as01.setPatternType("beep");
        as01.setIsActive(true);
        as01.setUpdatedAt(OffsetDateTime.now());
        ev01 = new EventTypeEntity();
        ev01.setId(UUID.randomUUID());
        ev01.setCode("EV-SOM-01");
        ev01.setName("EV-SOM-01");
        ev01.setEventCategoryId(UUID.randomUUID());
        ev01.setDefaultSeverityId(UUID.randomUUID());
        ev01.setDefaultSoundPatternId(as01.getId());
        ev01.setThresholdConfig(new LinkedHashMap<>(Map.of("blink_rate_max", 25)));
        ev01.setIsActive(true);
        ev01.setStatus("PUBLISHED");
        ev01.setStatusCategory("ACTIVE");
        ev01.setUpdatedAt(OffsetDateTime.now());
    }

    @Test
    void getOrCreateCreatesRowWhenAbsent() {
        when(globalConfigRepository.findById(GlobalConfigEntity.SINGLETON_ID))
                .thenReturn(Optional.empty());
        when(globalConfigRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = service.getOrCreate();

        assertEquals(1, result.version());
        var captor = ArgumentCaptor.forClass(GlobalConfigEntity.class);
        verify(globalConfigRepository).save(captor.capture());
        assertEquals(1, captor.getValue().getVersion());
    }

    @Test
    void getOrCreateReturnsExisting() {
        GlobalConfigEntity g = new GlobalConfigEntity();
        g.setVersion(7);
        g.setUpdatedAt(OffsetDateTime.now());
        when(globalConfigRepository.findById(GlobalConfigEntity.SINGLETON_ID))
                .thenReturn(Optional.of(g));

        assertEquals(7, service.getOrCreate().version());
    }

    @Test
    void bumpIncrementsAndWritesHistoryWithSnapshot() {
        GlobalConfigEntity g = new GlobalConfigEntity();
        g.setVersion(5);
        when(globalConfigRepository.lockById(GlobalConfigEntity.SINGLETON_ID))
                .thenReturn(Optional.of(g));
        when(eventTypeRepository.findByDeletedAtIsNull()).thenReturn(List.of(ev01));
        when(soundPatternRepository.findByIsActiveTrue()).thenReturn(List.of(as01));

        var result = service.bump(actor);

        assertEquals(6, result.version());
        assertEquals(actor, g.getUpdatedBy());
        var histCaptor = ArgumentCaptor.forClass(GlobalConfigHistoryEntity.class);
        verify(historyRepository).save(histCaptor.capture());
        assertEquals(6, histCaptor.getValue().getVersion());
        assertEquals(actor, histCaptor.getValue().getCreatedBy());
        Map<String, Object> snapshot = histCaptor.getValue().getSnapshotJson();
        assertEquals(6, snapshot.get("version"));
        assertNotNull(((Map<?, ?>) snapshot.get("thresholds")).get("EV-SOM-01"));
    }

    @Test
    void statusForDetectsStaleOnlyManual() {
        GlobalConfigEntity g = new GlobalConfigEntity();
        g.setVersion(16);
        g.setUpdatedAt(OffsetDateTime.now());
        when(globalConfigRepository.findById(GlobalConfigEntity.SINGLETON_ID))
                .thenReturn(Optional.of(g));

        // Solo manual flag activa pending; applied < global NO lo hace
        var notStale = service.statusFor(15, false);
        assertFalse(notStale.pending());
        assertEquals(16, notStale.availableVersion());

        var fresh = service.statusFor(16, false);
        assertFalse(fresh.pending());

        var manual = service.statusFor(16, true);
        assertTrue(manual.pending());

        var never = service.statusFor(null, false);
        assertFalse(never.pending());
    }
}
