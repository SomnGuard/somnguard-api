package com.somnguard.device_management.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.somnguard.parameterization.adapter.out.persistence.entity.EventTypeEntity;
import com.somnguard.parameterization.adapter.out.persistence.entity.SoundPatternEntity;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeviceConfigMergeServiceTest {

    @Test
    void overrideWinsOverCatalog() {
        var catalog = catalog();
        Map<String, Object> overrides = new LinkedHashMap<>();
        overrides.put("thresholds", Map.of("EV-SOM-01", Map.of("blink_rate_max", 22)));
        overrides.put("event_sound_map", Map.of("EV-SOM-01", "AS-02"));
        overrides.put("volume_pct", 50);

        Map<String, Object> effective = DeviceConfigMergeService.buildEffective(overrides,
                catalog.events(), catalog.sounds());

        @SuppressWarnings("unchecked")
        Map<String, Object> thresholds = (Map<String, Object>) effective.get("thresholds");
        @SuppressWarnings("unchecked")
        Map<String, Object> ev01 = (Map<String, Object>) thresholds.get("EV-SOM-01");
        assertEquals(22, ev01.get("blink_rate_max"));
        assertEquals(15, ev01.get("window_sec"));
        @SuppressWarnings("unchecked")
        Map<String, Object> soundMap = (Map<String, Object>) effective.get("event_sound_map");
        assertEquals("AS-02", soundMap.get("EV-SOM-01"));
        assertEquals(50, effective.get("volume_pct"));
        assertEquals(0.5, effective.get("volume_scale"));
    }

    @Test
    void noOverridesReturnsCatalogDefaults() {
        var catalog = catalog();

        Map<String, Object> effective = DeviceConfigMergeService.buildEffective(Map.of(),
                catalog.events(), catalog.sounds());

        @SuppressWarnings("unchecked")
        Map<String, Object> thresholds = (Map<String, Object>) effective.get("thresholds");
        assertTrue(thresholds.containsKey("EV-SOM-01"));
        assertEquals(80, effective.get("volume_pct"));
        assertEquals(30, effective.get("sync_interval_sec"));
        assertEquals(30, effective.get("sync_interval_seconds"));
    }

    @Test
    void catalogChangePropagatesWithoutOverrides() {
        var before = catalog();
        Map<String, Object> first = DeviceConfigMergeService.buildEffective(Map.of(),
                before.events(), before.sounds());

        EventTypeEntity changed = event("EV-SOM-01", Map.of("blink_rate_max", 99, "window_sec", 15));
        Map<String, Object> second = DeviceConfigMergeService.buildEffective(Map.of(),
                List.of(changed), before.sounds());

        @SuppressWarnings("unchecked")
        Map<String, Object> t1 = (Map<String, Object>) ((Map<String, Object>) first.get("thresholds")).get("EV-SOM-01");
        @SuppressWarnings("unchecked")
        Map<String, Object> t2 = (Map<String, Object>) ((Map<String, Object>) second.get("thresholds")).get("EV-SOM-01");
        assertEquals(25, t1.get("blink_rate_max"));
        assertEquals(99, t2.get("blink_rate_max"));
    }

    @Test
    void soundTweaksApplyOnProjectedDetails() {
        var catalog = catalog();
        Map<String, Object> overrides = Map.of(
                "sound_pattern_tweaks", Map.of("AS-01", Map.of("frequency_hz", 700)));

        Map<String, Object> effective = DeviceConfigMergeService.buildEffective(overrides,
                catalog.events(), catalog.sounds());

        @SuppressWarnings("unchecked")
        Map<String, Object> sounds = (Map<String, Object>) effective.get("sound_patterns");
        @SuppressWarnings("unchecked")
        Map<String, Object> as01 = (Map<String, Object>) sounds.get("AS-01");
        assertEquals(700, as01.get("frequency_hz"));
        assertEquals(0.5, as01.get("duration_sec"));
    }

    private record Catalog(List<EventTypeEntity> events, List<SoundPatternEntity> sounds) {}

    private Catalog catalog() {
        SoundPatternEntity as01 = sound("AS-01", 800, 500);
        SoundPatternEntity as02 = sound("AS-02", 950, 400);
        EventTypeEntity ev01 = event("EV-SOM-01", Map.of("blink_rate_max", 25, "window_sec", 15));
        ev01.setDefaultSoundPatternId(as01.getId());
        return new Catalog(List.of(ev01), List.of(as01, as02));
    }

    private EventTypeEntity event(String code, Map<String, Object> thresholds) {
        EventTypeEntity e = new EventTypeEntity();
        e.setId(UUID.randomUUID());
        e.setCode(code);
        e.setName(code);
        e.setEventCategoryId(UUID.randomUUID());
        e.setDefaultSeverityId(UUID.randomUUID());
        e.setDefaultSoundPatternId(UUID.randomUUID());
        e.setThresholdConfig(new LinkedHashMap<>(thresholds));
        e.setIsActive(true);
        e.setStatus("PUBLISHED");
        e.setStatusCategory("ACTIVE");
        e.setUpdatedAt(OffsetDateTime.now());
        return e;
    }

    private SoundPatternEntity sound(String code, int freqHz, int durationMs) {
        SoundPatternEntity s = new SoundPatternEntity();
        s.setId(UUID.randomUUID());
        s.setCode(code);
        s.setDescription(code);
        s.setFrequencyHz(freqHz);
        s.setDurationMs(durationMs);
        s.setRepetitions((short) 1);
        s.setPatternType("beep");
        s.setIsActive(true);
        s.setUpdatedAt(OffsetDateTime.now());
        return s;
    }
}
