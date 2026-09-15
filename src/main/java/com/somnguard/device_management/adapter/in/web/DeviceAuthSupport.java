package com.somnguard.device_management.adapter.in.web;

import com.somnguard.platform.security.ApiKeyAuthenticationFilter;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

final class DeviceAuthSupport {

    static final UUID SYSTEM_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private DeviceAuthSupport() {}

    static UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            try {
                return UUID.fromString(jwt.getToken().getSubject());
            } catch (IllegalArgumentException ex) {
                return SYSTEM_ID;
            }
        }
        return SYSTEM_ID;
    }

    static List<String> currentFeatures() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            Object claim = jwt.getToken().getClaim("features");
            if (claim instanceof List<?> list) {
                return list.stream().map(Object::toString).toList();
            }
            return List.of();
        }
        if (auth != null) {
            return auth.getAuthorities().stream().map(a -> a.getAuthority()).toList();
        }
        return List.of();
    }

    static boolean isAdmin() {
        List<String> features = currentFeatures();
        return features.contains("device.write") || features.contains("device.config_write")
                || features.contains("device.config") // legacy pre-25-features, remover tras migración
                || features.contains("ROLE_ADMIN");
    }

    static boolean isJwt() {
        return SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken;
    }

    static UUID currentDeviceId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof ApiKeyAuthenticationFilter.DevicePrincipal p) {
            return p.deviceId();
        }
        return null;
    }
}
