package com.somnguard.parameterization.adapter.in.web;

import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

final class AuditSupport {

    static final UUID SYSTEM_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private AuditSupport() {}

    static UUID currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            try {
                return UUID.fromString(jwt.getToken().getSubject());
            } catch (IllegalArgumentException ex) {
                return SYSTEM_USER_ID;
            }
        }
        return SYSTEM_USER_ID;
    }
}
