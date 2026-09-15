package com.somnguard.device_management.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DeviceConfigNormalizerTest {

    @Test
    void docsShapeMigratesToCanonical() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("thresholds", Map.of("blink_rate_max", 25));
        raw.put("sound_patterns", Map.of("EV-SOM-01", "AS-01"));
        raw.put("volume_pct", 80);
        raw.put("sync_interval_seconds", 30);
        raw.put("retention_days", 7);

        Map<String, Object> out = DeviceConfigNormalizer.normalize(raw);

        assertEquals(Map.of("blink_rate_max", 25), out.get("detection_thresholds"));
        assertEquals(Map.of("EV-SOM-01", "AS-01"), out.get("event_sound_map"));
        assertEquals(80, out.get("volume_pct"));
        assertEquals(30, out.get("sync_interval_sec"));
        assertEquals(1, out.get("schema_version"));
        assertTrue(!out.containsKey("sound_patterns"));
    }

    @Test
    void perEventThresholdsStayPerEvent() {
        Map<String, Object> raw = Map.of("thresholds", Map.of("EV-SOM-01", Map.of("blink_rate_max", 22)));

        Map<String, Object> out = DeviceConfigNormalizer.normalize(raw);

        assertEquals(Map.of("EV-SOM-01", Map.of("blink_rate_max", 22)), out.get("thresholds"));
        assertTrue(!out.containsKey("detection_thresholds"));
    }

    @Test
    void deviceSoundDefinitionsMigrateToTweaks() {
        Map<String, Object> raw = Map.of("sound_patterns", Map.of("AS-01", Map.of("frequency_hz", 800)));

        Map<String, Object> out = DeviceConfigNormalizer.normalize(raw);

        assertEquals(Map.of("AS-01", Map.of("frequency_hz", 800)), out.get("sound_pattern_tweaks"));
        assertTrue(!out.containsKey("event_sound_map"));
    }

    @Test
    void volumeScaleConvertsToPct() {
        Map<String, Object> out = DeviceConfigNormalizer.normalize(Map.of("volume_scale", 0.8));

        assertEquals(80, out.get("volume_pct"));
    }

    @Test
    void volumeOutOfRangeIs400() {
        assertThrows(IllegalArgumentException.class,
                () -> DeviceConfigNormalizer.normalize(Map.of("volume_pct", 101)));
        assertThrows(IllegalArgumentException.class,
                () -> DeviceConfigNormalizer.normalize(Map.of("volume_scale", 1.5)));
        assertThrows(IllegalArgumentException.class,
                () -> DeviceConfigNormalizer.normalize(Map.of("sync_interval_sec", 2)));
        assertThrows(IllegalArgumentException.class,
                () -> DeviceConfigNormalizer.normalize(Map.of("retention_days", 31)));
    }

    @Test
    void ambiguousSoundPatternsIs400() {
        assertThrows(IllegalArgumentException.class, () -> DeviceConfigNormalizer.normalize(
                Map.of("sound_patterns", Map.of("EV-SOM-01", Map.of("frequency_hz", 1)))));
    }

    @Test
    void unknownKeysPassThrough() {
        Map<String, Object> out = DeviceConfigNormalizer.normalize(Map.of("future_key", "keep-me"));

        assertEquals("keep-me", out.get("future_key"));
    }

    @Test
    void nullInputYieldsVersionedEmpty() {
        Map<String, Object> out = DeviceConfigNormalizer.normalize(null);

        assertEquals(1, out.get("schema_version"));
        assertEquals(1, out.size());
    }
}
