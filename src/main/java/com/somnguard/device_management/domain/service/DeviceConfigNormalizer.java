package com.somnguard.device_management.domain.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Normalización de overrides del PATCH (HU-API-005 AC-001, opción D).
 * Convierte alias legacy a forma canónica y valida estructura y rangos.
 * Errores estructurales lanzan {@link IllegalArgumentException} (mapea a 400).
 * La validación referencial (EV/AS vivos) la hace el caso de uso (mapea a 422).
 * Claves desconocidas se copian tal cual (compatibilidad hacia adelante).
 */
public final class DeviceConfigNormalizer {

    public static final int SCHEMA_VERSION = 1;

    private static final Pattern EV_CODE = Pattern.compile("^EV-[A-Z]+-[0-9]+$");
    private static final Pattern AS_CODE = Pattern.compile("^AS-[0-9]{2}$");

    private static final Set<String> KNOWN_KEYS = Set.of(
            "thresholds", "detection_thresholds", "sound_patterns",
            "event_sound_map", "sound_pattern_tweaks",
            "volume_pct", "volume_scale",
            "sync_interval_sec", "sync_interval_seconds",
            "heartbeat_interval_sec", "retention_days",
            "sensitivity", "camera_resolution", "camera_fps", "buffer_limit_mb",
            "schema_version");

    private DeviceConfigNormalizer() {}

    public static Map<String, Object> normalize(Map<String, Object> raw) {
        Map<String, Object> src = raw == null ? Map.of() : raw;
        Map<String, Object> thresholds = new LinkedHashMap<>();
        Map<String, Object> detection = new LinkedHashMap<>();
        Map<String, Object> soundMap = new LinkedHashMap<>();
        Map<String, Object> tweaks = new LinkedHashMap<>();
        Map<String, Object> out = new LinkedHashMap<>();

        for (Map.Entry<String, Object> e : src.entrySet()) {
            String key = e.getKey();
            if (!KNOWN_KEYS.contains(key)) {
                out.put(key, e.getValue());
            }
        }

        if (src.containsKey("thresholds")) {
            splitThresholds(requireMap(src.get("thresholds"), "thresholds"), thresholds, detection);
        }
        if (src.containsKey("detection_thresholds")) {
            detection.putAll(requireMap(src.get("detection_thresholds"), "detection_thresholds"));
        }
        if (src.containsKey("sound_patterns")) {
            splitSoundPatterns(requireMap(src.get("sound_patterns"), "sound_patterns"), soundMap, tweaks);
        }
        if (src.containsKey("event_sound_map")) {
            for (Map.Entry<String, Object> e : requireMap(src.get("event_sound_map"), "event_sound_map").entrySet()) {
                soundMap.put(requireEvCode(e.getKey()), requireAsCode(e.getValue(), "event_sound_map." + e.getKey()));
            }
        }
        if (src.containsKey("sound_pattern_tweaks")) {
            for (Map.Entry<String, Object> e : requireMap(src.get("sound_pattern_tweaks"), "sound_pattern_tweaks").entrySet()) {
                tweaks.put(requireAsKey(e.getKey()), requireMap(e.getValue(), "sound_pattern_tweaks." + e.getKey()));
            }
        }

        normalizeVolume(src, out);
        normalizeSync(src, out);
        normalizeOptionalScalars(src, out);

        if (!thresholds.isEmpty()) out.put("thresholds", thresholds);
        if (!detection.isEmpty()) out.put("detection_thresholds", detection);
        if (!soundMap.isEmpty()) out.put("event_sound_map", soundMap);
        if (!tweaks.isEmpty()) out.put("sound_pattern_tweaks", tweaks);
        out.put("schema_version", SCHEMA_VERSION);
        return out;
    }

    private static void splitThresholds(Map<String, Object> value, Map<String, Object> perEvent,
            Map<String, Object> flat) {
        boolean anyEv = value.keySet().stream().anyMatch(k -> EV_CODE.matcher(k).matches());
        if (anyEv) {
            for (Map.Entry<String, Object> e : value.entrySet()) {
                perEvent.put(requireEvCode(e.getKey()), requireMap(e.getValue(), "thresholds." + e.getKey()));
            }
        } else {
            flat.putAll(value);
        }
    }

    private static void splitSoundPatterns(Map<String, Object> value, Map<String, Object> soundMap,
            Map<String, Object> tweaks) {
        for (Map.Entry<String, Object> e : value.entrySet()) {
            String key = e.getKey();
            Object val = e.getValue();
            if (EV_CODE.matcher(key).matches() && val instanceof String s) {
                soundMap.put(key, requireAsCode(s, "sound_patterns." + key));
            } else if (AS_CODE.matcher(key).matches() && val instanceof Map<?, ?> m) {
                @SuppressWarnings("unchecked")
                Map<String, Object> tweak = (Map<String, Object>) m;
                tweaks.put(key, tweak);
            } else {
                throw new IllegalArgumentException(
                        "sound_patterns." + key + " debe ser EV-XXX->\"AS-XX\" o AS-XX->{...}");
            }
        }
    }

    private static void normalizeVolume(Map<String, Object> src, Map<String, Object> out) {
        if (src.containsKey("volume_pct")) {
            int pct = asInt(src.get("volume_pct"), "volume_pct");
            if (pct < 0 || pct > 100) throw new IllegalArgumentException("volume_pct debe estar entre 0 y 100");
            out.put("volume_pct", pct);
        } else if (src.containsKey("volume_scale")) {
            double scale = asDouble(src.get("volume_scale"), "volume_scale");
            if (scale < 0.0 || scale > 1.0) throw new IllegalArgumentException("volume_scale debe estar entre 0.0 y 1.0");
            out.put("volume_pct", (int) Math.round(scale * 100.0));
        }
    }

    private static void normalizeSync(Map<String, Object> src, Map<String, Object> out) {
        if (src.containsKey("sync_interval_sec") || src.containsKey("sync_interval_seconds")) {
            Object v = src.containsKey("sync_interval_sec") ? src.get("sync_interval_sec") : src.get("sync_interval_seconds");
            int sec = asInt(v, "sync_interval_sec");
            if (sec < 5 || sec > 3600) throw new IllegalArgumentException("sync_interval_sec debe estar entre 5 y 3600");
            out.put("sync_interval_sec", sec);
        }
        if (src.containsKey("heartbeat_interval_sec")) {
            int hb = asInt(src.get("heartbeat_interval_sec"), "heartbeat_interval_sec");
            if (hb < 5 || hb > 600) throw new IllegalArgumentException("heartbeat_interval_sec debe estar entre 5 y 600");
            out.put("heartbeat_interval_sec", hb);
        }
        if (src.containsKey("retention_days")) {
            int days = asInt(src.get("retention_days"), "retention_days");
            if (days < 1 || days > 30) throw new IllegalArgumentException("retention_days debe estar entre 1 y 30");
            out.put("retention_days", days);
        }
    }

    private static void normalizeOptionalScalars(Map<String, Object> src, Map<String, Object> out) {
        if (src.containsKey("sensitivity")) {
            Object v = src.get("sensitivity");
            if (!(v instanceof String s) || s.isBlank()) throw new IllegalArgumentException("sensitivity debe ser texto no vacío");
            out.put("sensitivity", s);
        }
        if (src.containsKey("camera_fps")) {
            int fps = asInt(src.get("camera_fps"), "camera_fps");
            if (fps < 1 || fps > 60) throw new IllegalArgumentException("camera_fps debe estar entre 1 y 60");
            out.put("camera_fps", fps);
        }
        if (src.containsKey("camera_resolution")) {
            out.put("camera_resolution", requireResolution(src.get("camera_resolution")));
        }
        if (src.containsKey("buffer_limit_mb")) {
            double mb = asDouble(src.get("buffer_limit_mb"), "buffer_limit_mb");
            if (mb <= 0) throw new IllegalArgumentException("buffer_limit_mb debe ser > 0");
            out.put("buffer_limit_mb", mb);
        }
    }

    private static List<Integer> requireResolution(Object v) {
        if (!(v instanceof List<?> list) || list.size() != 2) {
            throw new IllegalArgumentException("camera_resolution debe ser [ancho, alto]");
        }
        int w = asInt(list.get(0), "camera_resolution[0]");
        int h = asInt(list.get(1), "camera_resolution[1]");
        if (w <= 0 || h <= 0) throw new IllegalArgumentException("camera_resolution debe ser > 0");
        return List.of(w, h);
    }

    private static String requireEvCode(String code) {
        if (code == null || !EV_CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("Código de evento inválido: " + code + " (esperado EV-XXX-NN)");
        }
        return code;
    }

    private static String requireAsKey(String code) {
        if (code == null || !AS_CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("Código de sonido inválido: " + code + " (esperado AS-NN)");
        }
        return code;
    }

    private static String requireAsCode(Object v, String field) {
        if (!(v instanceof String s) || !AS_CODE.matcher(s).matches()) {
            throw new IllegalArgumentException(field + " debe ser un código AS-NN");
        }
        return s;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> requireMap(Object v, String field) {
        if (!(v instanceof Map)) throw new IllegalArgumentException(field + " debe ser un objeto");
        return (Map<String, Object>) v;
    }

    private static int asInt(Object v, String field) {
        if (v instanceof Number n) return n.intValue();
        throw new IllegalArgumentException(field + " debe ser numérico");
    }

    private static double asDouble(Object v, String field) {
        if (v instanceof Number n) return n.doubleValue();
        throw new IllegalArgumentException(field + " debe ser numérico");
    }
}
