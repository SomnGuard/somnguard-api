package com.somnguard.device_management.domain.service;

import com.somnguard.parameterization.adapter.out.persistence.entity.EventTypeEntity;
import com.somnguard.parameterization.adapter.out.persistence.entity.SoundPatternEntity;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Merge en lectura (HU-API-005 AC-002/AC-005, opción D).
 * Defaults vivos de catálogo + overrides canónicos; precedencia override &gt; catálogo.
 * Nunca persiste: solo construye la vista efectiva y su proyección device-native.
 */
public final class DeviceConfigMergeService {

    public static final int DEFAULT_VOLUME_PCT = 80;
    public static final int DEFAULT_SYNC_INTERVAL_SEC = 30;
    public static final int DEFAULT_HEARTBEAT_INTERVAL_SEC = 30;
    public static final int DEFAULT_RETENTION_DAYS = 7;
    public static final double DEFAULT_SOUND_VOLUME = 0.5;

    private DeviceConfigMergeService() {}

    public static Map<String, Object> buildEffective(Map<String, Object> overrides,
            List<EventTypeEntity> eventTypes, List<SoundPatternEntity> sounds) {
        Map<String, Object> ov = overrides == null ? Map.of() : overrides;
        Map<String, String> soundCodeById = new LinkedHashMap<>();
        Map<String, SoundPatternEntity> soundByCode = new LinkedHashMap<>();
        for (SoundPatternEntity s : sounds) {
            if (Boolean.TRUE.equals(s.getIsActive())) {
                soundCodeById.put(s.getId().toString(), s.getCode());
                soundByCode.put(s.getCode(), s);
            }
        }

        Map<String, Object> defaultThresholds = new LinkedHashMap<>();
        Map<String, Object> defaultSoundMap = new LinkedHashMap<>();
        for (EventTypeEntity e : eventTypes) {
            if (!Boolean.TRUE.equals(e.getIsActive()) || e.getDeletedAt() != null) continue;
            if (!"PUBLISHED".equalsIgnoreCase(e.getStatus())) continue;
            defaultThresholds.put(e.getCode(), copyMap(e.getThresholdConfig()));
            String soundCode = soundCodeById.get(String.valueOf(e.getDefaultSoundPatternId()));
            if (soundCode != null) defaultSoundMap.put(e.getCode(), soundCode);
        }

        Map<String, Object> thresholds = copyMap(defaultThresholds);
        deepMergeInto(thresholds, asMap(ov.get("thresholds")));

        Map<String, Object> eventSoundMap = new LinkedHashMap<>(defaultSoundMap);
        eventSoundMap.putAll(asMap(ov.get("event_sound_map")));

        Map<String, Object> soundDetails = new LinkedHashMap<>();
        for (Map.Entry<String, SoundPatternEntity> e : soundByCode.entrySet()) {
            soundDetails.put(e.getKey(), projectSound(e.getValue()));
        }
        Map<String, Object> tweaks = asMap(ov.get("sound_pattern_tweaks"));
        for (Map.Entry<String, Object> e : tweaks.entrySet()) {
            Object base = soundDetails.get(e.getKey());
            Map<String, Object> merged = base instanceof Map<?, ?> m ? copyMap(asMap(m)) : new LinkedHashMap<>();
            if (e.getValue() instanceof Map<?, ?> tm) merged.putAll(asMap(tm));
            soundDetails.put(e.getKey(), merged);
        }

        int volumePct = intOr(ov.get("volume_pct"), DEFAULT_VOLUME_PCT);
        int syncSec = intOr(ov.get("sync_interval_sec"), DEFAULT_SYNC_INTERVAL_SEC);
        int hbSec = intOr(ov.get("heartbeat_interval_sec"), DEFAULT_HEARTBEAT_INTERVAL_SEC);
        int retention = intOr(ov.get("retention_days"), DEFAULT_RETENTION_DAYS);

        Map<String, Object> effective = new LinkedHashMap<>();
        effective.put("schema_version", DeviceConfigNormalizer.SCHEMA_VERSION);
        effective.put("thresholds", thresholds);
        effective.put("event_sound_map", eventSoundMap);
        effective.put("detection_thresholds", copyMap(asMap(ov.get("detection_thresholds"))));
        effective.put("sound_patterns", soundDetails);
        effective.put("volume_pct", volumePct);
        effective.put("volume_scale", volumePct / 100.0);
        effective.put("sync_interval_sec", syncSec);
        effective.put("sync_interval_seconds", syncSec);
        effective.put("heartbeat_interval_sec", hbSec);
        effective.put("retention_days", retention);
        for (String passthrough : List.of("sensitivity", "camera_resolution", "camera_fps", "buffer_limit_mb")) {
            if (ov.containsKey(passthrough)) effective.put(passthrough, ov.get(passthrough));
        }
        for (Map.Entry<String, Object> e : ov.entrySet()) {
            if (!effective.containsKey(e.getKey())
                    && !"sound_pattern_tweaks".equals(e.getKey())
                    && !"schema_version".equals(e.getKey())) {
                effective.put(e.getKey(), e.getValue());
            }
        }
        return effective;
    }

    public static OffsetDateTime maxCatalogUpdatedAt(List<EventTypeEntity> eventTypes,
            List<SoundPatternEntity> sounds) {
        OffsetDateTime max = null;
        for (EventTypeEntity e : eventTypes) max = later(max, e.getUpdatedAt());
        for (SoundPatternEntity s : sounds) max = later(max, s.getUpdatedAt());
        return max;
    }

    private static OffsetDateTime later(OffsetDateTime a, OffsetDateTime b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isAfter(b) ? a : b;
    }

    private static Map<String, Object> projectSound(SoundPatternEntity s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("frequency_hz", s.getFrequencyHz());
        double durationSec = s.getDurationMs() == null ? DEFAULT_SOUND_VOLUME : s.getDurationMs() / 1000.0;
        m.put("duration_sec", durationSec);
        m.put("repetitions", s.getRepetitions() == null ? 1 : s.getRepetitions().intValue());
        double intervalSec = s.getIntervalMs() == null ? 0.0 : s.getIntervalMs() / 1000.0;
        m.put("interval_sec", intervalSec);
        m.put("loop", s.getRepetitions() != null && s.getRepetitions() == 0);
        m.put("pattern_type", s.getPatternType());
        m.put("volume", DEFAULT_SOUND_VOLUME);
        return m;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> asMap(Object v) {
        if (v instanceof Map) return (Map<String, Object>) v;
        return Map.of();
    }

    static Map<String, Object> copyMap(Map<String, Object> src) {
        Map<String, Object> copy = new LinkedHashMap<>();
        if (src == null) return copy;
        for (Map.Entry<String, Object> e : src.entrySet()) {
            Object v = e.getValue();
            if (v instanceof Map<?, ?> m) copy.put(e.getKey(), copyMap(asMap(m)));
            else copy.put(e.getKey(), v);
        }
        return copy;
    }

    static void deepMergeInto(Map<String, Object> base, Map<String, Object> over) {
        for (Map.Entry<String, Object> e : over.entrySet()) {
            Object bv = base.get(e.getKey());
            if (bv instanceof Map && e.getValue() instanceof Map) {
                Map<String, Object> merged = copyMap(asMap(bv));
                deepMergeInto(merged, asMap(e.getValue()));
                base.put(e.getKey(), merged);
            } else {
                base.put(e.getKey(), e.getValue());
            }
        }
    }

    private static int intOr(Object v, int def) {
        if (v instanceof Number n) return n.intValue();
        return def;
    }
}
